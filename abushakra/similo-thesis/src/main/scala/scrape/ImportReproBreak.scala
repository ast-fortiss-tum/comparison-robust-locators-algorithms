package scrape

import elements.Element
import evalutation.ComputeClassification
import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import scala.jdk.CollectionConverters.*

/**
 * Imports the snapshots captured from ReproBreak's locator breaks (scripts/reprobreak/capture.py) as benchmark sites.
 *
 * Each break becomes the site rb_<repository>_<break id> with two versions: 2001-01-01 is the parent commit, where the
 * old locator selects the target; 2001-01-02 is the fixing commit, where the developers' new locator selects it. The
 * ground truth (xpaths.csv) pairs the two targets, so it comes from the projects' developers, not from the benchmark
 * authors. Elements get the benchmark's neighbour texts (as in LoadAllWebsites) and a synthetic URL listed in urls.csv;
 * classified.csv is computed with ComputeClassification. A break is imported only if both locators select exactly one
 * visible element of the main document that is among the extracted candidates; the outcome of every break is written
 * to sites/reprobreak_manifest.csv.
 *
 *   runMain scrape.ImportReproBreak <plan.json> <capture dir> [sites dir]
 */
object ImportReproBreak:

  val OldDate = "2001-01-01"
  val NewDate = "2001-01-02"

  case class PlanEntry(id: Int, repo: String, commit: String, parent: String, file: String, framework: String,
                       old_locator: String, new_locator: String)

  case class Capture(elements: Seq[Element], dom: String, targets: Seq[Json], matches: Int, screenshot: Option[Path])

  def main(args: Array[String]): Unit =
    val planPath = Paths.get(args(0))
    val captures = Paths.get(args(1))
    val sitesDir = Paths.get(if args.length > 2 then args(2) else "sites")
    val plan = parse(Files.readString(planPath)).flatMap(_.as[Seq[PlanEntry]]).fold(throw _, identity)
    val manifest = Seq.newBuilder[String]
    manifest += "site;id;repo;commit;status;old_matches;new_matches;old_tag;new_tag;old_xpath;new_xpath;old_text;new_text;old_locator;new_locator"
    for e <- plan do
      val repo = e.repo.split("/").last
      val site = s"rb_${repo.replace('-', '_')}_${e.id}"
      val old = load(captures.resolve(s"$repo/old/${e.parent}"), e.id)
      val nw = load(captures.resolve(s"$repo/new/${e.commit}"), e.id)
      val status = (old, nw) match
        case (None, _) => "no old capture"
        case (_, None) => "no new capture"
        case (Some(o), Some(n)) => check(o, "old").orElse(check(n, "new")).getOrElse("ok")
      def target(c: Option[Capture]) = c.flatMap(_.targets.headOption).flatMap(t => t.hcursor.downField("element").as[Element].toOption)
      val (ot, nt) = (target(old), target(nw))
      def clean(s: Option[String]) = s.getOrElse("").replaceAll("[;\\n\\r]+", " ").take(80)
      manifest += Seq(site, e.id, e.repo, e.commit, status, old.fold("")(_.matches.toString), nw.fold("")(_.matches.toString),
        ot.fold("")(_.tag), nt.fold("")(_.tag), ot.fold("")(_.xpath.chrome), nt.fold("")(_.xpath.chrome),
        clean(ot.flatMap(_.innerText)), clean(nt.flatMap(_.innerText)), clean(Some(e.old_locator)), clean(Some(e.new_locator))).mkString(";")
      if status == "ok" then write(sitesDir, site, old.get, ot.get, nw.get, nt.get)
      println(s"$site: $status")
    Files.writeString(sitesDir.resolve("reprobreak_manifest.csv"), manifest.result().mkString("\n") + "\n", UTF_8)

  /** The first occurrence of the capture of break `id` (chromium preferred), with its screenshot. */
  private def load(dir: Path, id: Int): Option[Capture] =
    if !Files.isDirectory(dir) then return None
    val names = Seq(s"$id-1-chromium.json", s"$id-1.json") ++
      Option(dir.toFile.list()).toSeq.flatten.filter(n => n.startsWith(s"$id-1-") && n.endsWith(".json")).sorted
    names.map(dir.resolve).find(Files.exists(_)).map { file =>
      val json = parse(Files.readString(file)).fold(throw _, identity)
      val c = json.hcursor
      val png = Paths.get(file.toString.stripSuffix(".json") + ".png")
      val cypressShot = Option(dir.resolve("shots").toFile).filter(_.isDirectory).flatMap(d =>
        Files.walk(d.toPath).iterator().asScala.find(_.getFileName.toString == s"rb-$id-1.png"))
      Capture(
        c.downField("elements").as[Seq[Element]].fold(throw _, identity),
        c.downField("dom").as[String].getOrElse(""),
        c.downField("targets").as[Seq[Json]].getOrElse(Nil),
        c.downField("matches").as[Int].getOrElse(0),
        Some(png).filter(Files.exists(_)).orElse(cypressShot))
    }

  private def check(c: Capture, side: String): Option[String] =
    def flag(name: String) = c.targets.headOption.flatMap(_.hcursor.downField(name).as[Boolean].toOption).getOrElse(false)
    val xpath = c.targets.headOption.flatMap(_.hcursor.downField("element").downField("xpath").downField("chrome").as[String].toOption)
    if c.matches == 0 then Some(s"$side locator selects nothing")
    else if c.matches > 1 then Some(s"$side locator selects ${c.matches} elements")
    else if !flag("main") then Some(s"$side target in a frame")
    else if !flag("visible") then Some(s"$side target not visible")
    else if !xpath.exists(x => c.elements.exists(_.xpath.chrome == x)) then Some(s"$side target not a candidate")
    else if c.screenshot.isEmpty then Some(s"$side screenshot missing")
    else None

  private def write(sitesDir: Path, site: String, old: Capture, oldTarget: Element, nw: Capture, newTarget: Element): Unit =
    val dir = sitesDir.resolve(site)
    for (date, c) <- Seq(OldDate -> old, NewDate -> nw) do
      val d = dir.resolve(date)
      Files.createDirectories(d)
      val url = s"http://reprobreak.local/$site/$date"
      val withUrl = c.elements.map(_.copy(url = Some(url)))
      val processed = withUrl.map(_.addNeighborTexts(withUrl))
      Files.writeString(d.resolve("elements.json"), processed.asJson.spaces2, UTF_8)
      Files.writeString(d.resolve("chromeDOM.html"), c.dom, UTF_8)
      Files.copy(c.screenshot.get, d.resolve("screenshot.png"), StandardCopyOption.REPLACE_EXISTING)
    Files.writeString(dir.resolve("urls.csv"),
      s"date;url\n$OldDate;http://reprobreak.local/$site/$OldDate\n$NewDate;http://reprobreak.local/$site/$NewDate\n", UTF_8)
    Files.writeString(dir.resolve("xpaths.csv"), s"$OldDate;$NewDate\n${oldTarget.xpath.chrome};${newTarget.xpath.chrome}\n", UTF_8)
    ComputeClassification.computeForXpaths(dir.resolve("xpaths.csv"))
