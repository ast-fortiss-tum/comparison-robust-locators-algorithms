package scrape

import elements.Element
import evalutation.ComputeClassification
import io.circe.generic.auto.*
import io.circe.parser.parse

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, Paths, StandardCopyOption}

/**
 * Imports the identity-oracle chains written by scripts/oracle/chains.py as multi-version benchmark sites (one site per
 * UI state, like the Wayback sites: sites/<site>/<date>/{elements.json,chromeDOM.html,screenshot.png}, urls.csv,
 * xpaths.csv, classified.csv). The versions are releases, mapped to consecutive synthetic dates 2001-01-01, 2001-01-02, ...;
 * the manifest keeps the mapping. Row i of xpaths.csv is one oracle element, its cell the absolute XPath in that release
 * or "-" when the element is absent there. classified.csv is computed with ComputeClassification, as for ReproBreak.
 *
 *   runMain scrape.ImportOracle <manifest.json> [sites dir]
 */
object ImportOracle:

  case class Release(release: String, date: String, json: String, png: String)
  case class Row(id: String, xpaths: Seq[String])
  case class SiteEntry(site: String, state: String, releases: Seq[Release], rows: Seq[Row])

  def main(args: Array[String]): Unit =
    val manifest = parse(Files.readString(Paths.get(args(0)))).flatMap(_.as[Seq[SiteEntry]]).fold(throw _, identity)
    val sitesDir = Paths.get(if args.length > 1 then args(1) else "sites")
    val summary = Seq.newBuilder[String]
    summary += "site;state;version;release;date"
    for s <- manifest do
      val dir = sitesDir.resolve(s.site)
      for r <- s.releases do
        val c = parse(Files.readString(Paths.get(r.json))).fold(throw _, _.hcursor)
        val elements = c.downField("elements").as[Seq[Element]].fold(throw _, identity)
        val dom = c.downField("dom").as[String].getOrElse("")
        val (sx, sy) = c.downField("scroll").as[(Int, Int)].getOrElse((0, 0))
        val viewportWidth = c.downField("viewport").downN(0).as[Int].getOrElse(0)
        val d = dir.resolve(r.date)
        Files.createDirectories(d)
        val url = s"http://oracle.local/${s.site}/${r.date}"
        val withUrl = elements.map(e => e.copy(url = Some(url), location = (e.location._1 + sx, e.location._2 + sy)))
        val processed = withUrl.map(_.addNeighborTexts(withUrl))
        import io.circe.syntax.*
        Files.writeString(d.resolve("elements.json"), processed.asJson.spaces2, UTF_8)
        Files.writeString(d.resolve("chromeDOM.html"), dom, UTF_8)
        Files.copy(Paths.get(r.png), d.resolve("screenshot.png"), StandardCopyOption.REPLACE_EXISTING)
        val pngWidth = javax.imageio.ImageIO.read(d.resolve("screenshot.png").toFile).getWidth
        val ratio = if viewportWidth > 0 then pngWidth.toDouble / viewportWidth else 1.0
        val scale = if ratio < 1.5 then 1.0 else Math.round(ratio).toDouble
        Files.writeString(d.resolve("screenshot_scale.txt"), String.format(java.util.Locale.ROOT, "%.4f\n", scale), UTF_8)
        summary += s"${s.site};${s.state};${s.releases.indexOf(r)};${r.release};${r.date}"
      val dates = s.releases.map(_.date)
      Files.writeString(dir.resolve("urls.csv"), ("date;url\n" + dates.map(d => s"$d;http://oracle.local/${s.site}/$d").mkString("\n") + "\n"), UTF_8)
      Files.writeString(dir.resolve("xpaths.csv"), dates.mkString(";") + "\n" + s.rows.map(_.xpaths.mkString(";")).mkString("\n") + "\n", UTF_8)
      Files.writeString(dir.resolve("ids.csv"), "row;id\n" + s.rows.zipWithIndex.map((r, i) => s"$i;${r.id}").mkString("\n") + "\n", UTF_8) // for validation only
      ComputeClassification.computeForXpaths(dir.resolve("xpaths.csv"))
      println(s"${s.site}: ${s.releases.size} versions, ${s.rows.size} elements")
    Files.writeString(sitesDir.resolve("oracle_versions.csv"), summary.result().mkString("\n") + "\n", UTF_8)
