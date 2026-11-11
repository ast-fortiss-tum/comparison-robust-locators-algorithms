package scrape

import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.{JavascriptExecutor, TimeoutException}
import util.Load

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.{Duration, LocalDateTime}
import java.util.logging.{Level, Logger}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.{Failure, Success, Try, Using}

/**
 * Regenerates sites/<site>/<date>/chromeDOM.html (the rendered DOM used by the DOM-based locators) by reloading
 * the Wayback snapshots listed in urls.csv.
 *
 * The original scrape ran under conditions that are not recorded (browser language, Wayback toolbar on or off,
 * how long the page was left to load). For each snapshot several rendering variants are tried and the one whose DOM
 * best matches the recorded dataset is kept: a tracked element is consistent if its recorded absolute XPath selects
 * exactly one element with the recorded tag and its recorded ID-XPath selects that same element. Choices are logged
 * to sites/regeneration_log.csv.
 *
 *   runMain scrape.RegenerateDoms fetch    all | <site> [<site> ...]
 *   runMain scrape.RegenerateDoms validate all | <site> [<site> ...]
 *   runMain scrape.RegenerateDoms diagnose <site> <date>
 */
object RegenerateDoms:

  private val SitesDir = "sites"
  private val LogFile = Paths.get(s"$SitesDir/regeneration_log.csv")
  // Polite crawling: the Wayback Machine blocked us once at a 2s pace with up to 6 variants per snapshot.
  private val MaxVariants = 3
  /** Alternative variants are only rendered when the best so far has fewer consistent elements than this. */
  private val AlternateBelow = 0.90
  private val PauseBetweenPageLoadsMs = 60_000L

  /** Raised when archive.org answers with its bot-block or rate-limit page: the whole run stops. */
  final class BlockedByArchive(msg: String) extends RuntimeException(msg)

  private val BlockMarkers = Seq("suspected abusive bot traffic", "Too Many Requests", "429 Too Many")

  def main(args: Array[String]): Unit =
    args.toList match
      case "fetch" :: sites if sites.nonEmpty    => fetch(resolveSites(sites))
      case "validate" :: sites if sites.nonEmpty => validate(resolveSites(sites))
      case "diagnose" :: site :: date :: Nil     => diagnose(site, date)
      case _ => println("usage: fetch|validate all|<site>...  or  diagnose <site> <date>")

  /** "all" = the sites the evaluation uses (Load.getFullLoadedSuitableSites). */
  private def resolveSites(args: Seq[String]): Seq[String] =
    if args == Seq("all") then Load.getFullLoadedSuitableSites.map(_.getFileName.toString).sorted else args

  // -------------------------------------------------------------- variants

  /**
   * @param german  browser language de-DE (else en-US).
   * @param toolbar load the snapshot with the Wayback toolbar (URL without "if_").
   * @param scroll  scroll through the page before capturing (else capture shortly after load).
   */
  case class Variant(german: Boolean, toolbar: Boolean, scroll: Boolean):
    override def toString: String =
      s"${if german then "de" else "en"}${if toolbar then "+toolbar" else ""}${if scroll then "+scroll" else "+early"}"

  /** Variants in order of prior likelihood; the first is the best guess from the recording itself. */
  private def variantsFor(site: String, date: String): Seq[Variant] =
    val toolbar = recordedWithToolbar(site, date)
    Seq(
      Variant(german = true, toolbar, scroll = true),
      Variant(german = true, !toolbar, scroll = true),
      Variant(german = false, toolbar, scroll = true),
      Variant(german = true, toolbar, scroll = false),
      Variant(german = false, toolbar, scroll = false),
      Variant(german = false, !toolbar, scroll = true)
    ).distinct.take(MaxVariants)

  /** Snapshots whose recorded elements include the Wayback toolbar (wm-ipp ...) were loaded without "if_". */
  private def recordedWithToolbar(site: String, date: String): Boolean =
    val f = Paths.get(s"$SitesDir/$site/$date/elements.json")
    Files.exists(f) && Files.readString(f).contains("wm-ipp")

  // ---------------------------------------------------------------- drivers

  private val drivers = mutable.Map.empty[Boolean, ChromeDriver]

  private def driver(german: Boolean): ChromeDriver =
    drivers.getOrElseUpdate(german, newDriver(german))

  private def resetDrivers(): Unit =
    drivers.values.foreach(d => Try(d.quit()))
    drivers.clear()

  private def newDriver(german: Boolean): ChromeDriver =
    Logger.getLogger("org.openqa.selenium").setLevel(Level.OFF)
    System.setProperty("webdriver.chrome.driver", "driver/chrome/chromedriver-mac-arm64/chromedriver")
    val lang = if german then "de-DE" else "en-US"
    val options = new ChromeOptions()
      .setBinary("driver/chrome/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing")
      // The original screenshots are 3840px wide at DPR 2, i.e. a 1920px-wide viewport
      .addArguments("--headless=new", "--window-size=1920,1080", "--force-device-scale-factor=2",
        "--disable-gpu", "--no-sandbox", "--disable-extensions", "--remote-allow-origins=*", s"--lang=$lang",
        // The original scrape used a visible browser: do not announce "HeadlessChrome", which sites react to
        "--user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36")
    options.setExperimentalOption("prefs", java.util.Map.of("intl.accept_languages",
      if german then "de-DE,de,en-US,en" else "en-US,en"))
    val d = new ChromeDriver(options)
    d.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(90))
    d.manage().timeouts().scriptTimeout(Duration.ofSeconds(30))
    d

  // ----------------------------------------------------------------- render

  /** date -> Wayback URL for the snapshot folders of a site. */
  private def snapshots(site: String): Seq[(String, String)] =
    val lines = Using.resource(scala.io.Source.fromFile(s"$SitesDir/$site/urls.csv"))(_.getLines().drop(1).toList)
    lines.filter(_.contains(";")).map(_.split(";", 2)).map(a => (a(0).trim, a(1).trim))
      .filter((d, _) => Files.isDirectory(Paths.get(s"$SitesDir/$site/$d")))
      .sortBy(_._1)

  /** Scroll down in steps so lazily loaded content is rendered, then back to the top (bounded). */
  private def scrollThrough(js: JavascriptExecutor): Unit =
    Try {
      val height = js.executeScript("return document.body ? document.body.scrollHeight : 0").asInstanceOf[Long]
      var y = 0L
      var steps = 0
      while y < height && steps < 40 do
        js.executeScript("window.scrollBy(0, 1000)")
        Thread.sleep(400)
        y += 1000
        steps += 1
      js.executeScript("window.scrollTo(0, 0)")
      Thread.sleep(1500)
    }

  /**
   * Browsers ignore xmlns on <html> in HTML documents (so the recorded XPaths work there), but the XHTML-to-JDOM
   * conversion would put every element into that namespace and no /html/... path would match.
   */
  private def dropRootNamespace(html: String): String =
    val start = html.indexOf("<html")
    val end = if start >= 0 then html.indexOf('>', start) else -1
    if start < 0 || end < 0 then html
    else html.substring(0, start) + html.substring(start, end).replaceAll("""\s+xmlns="[^"]*"""", "") + html.substring(end)

  private def render(url: String, v: Variant): String =
    val d = driver(v.german)
    val target = if v.toolbar then url.replaceFirst("""(/web/\d+)if_/""", "$1/") else url
    try d.get(target)
    catch case _: TimeoutException => () // keep whatever has rendered
    val js = d.asInstanceOf[JavascriptExecutor]
    if v.scroll then
      Thread.sleep(3000)
      scrollThrough(js)
    else Thread.sleep(1500)
    val html = js.executeScript("return document.documentElement.outerHTML").asInstanceOf[String]
    if BlockMarkers.exists(html.contains) && html.length < 20_000 then
      throw BlockedByArchive(s"archive.org block/rate-limit page received for $target")
    "<!DOCTYPE html>\n" + dropRootNamespace(html)

  // ------------------------------------------------------------------ score

  /** Target absolute XPaths per date, from xpaths.csv ('-' marks a deleted element). */
  private def targets(site: String): Map[String, Seq[String]] =
    val lines = Using.resource(scala.io.Source.fromFile(s"$SitesDir/$site/xpaths.csv"))(_.getLines().toList)
    val dates = lines.head.split(";").map(_.trim)
    val rows = lines.tail.filter(_.nonEmpty).map(_.split(";", -1).map(_.trim))
    dates.zipWithIndex.map((d, i) => d -> rows.flatMap(r => r.lift(i)).filter(_.startsWith("/")).toSeq).toMap

  /** Per-snapshot counts: targets, absResolved, idResolved, idAgrees (fully consistent), tagAgrees. */
  case class Score(targets: Int, abs: Int, id: Int, agree: Int, tag: Int):
    def fraction: Double = if targets == 0 then 1.0 else agree.toDouble / targets
    def perfect: Boolean = agree == targets
    /** Order: consistent elements first, then resolvable absolute XPaths, then ID-XPaths. */
    def key: (Int, Int, Int) = (agree, abs, id)

  private def score(site: String, date: String, domFile: Path): Score =
    val dir = Paths.get(s"$SitesDir/$site/$date")
    val ts = targets(site).getOrElse(date, Seq.empty)
    val doc = Load.htmlToJDOM(domFile.toFile)
    val elements = Load.getDateElementsFor(dir).map(e => e.xpath.chrome -> e).toMap
    var abs, id, agree, tag = 0
    for t <- ts do
      val byAbs = Load.getJDOMElementsByXPath(t, doc)
      val rec = elements.get(t)
      val tagOk = byAbs.size == 1 && rec.exists(e => byAbs.head.getName.equalsIgnoreCase(e.tag))
      if byAbs.size == 1 then abs += 1
      if tagOk then tag += 1
      rec.foreach { e =>
        val byId = Load.getJDOMElementsByXPath(e.xpath.idxpath, doc)
        if byId.size == 1 then
          id += 1
          if tagOk && (byAbs.head eq byId.head) then agree += 1
      }
    Score(ts.size, abs, id, agree, tag)

  // ------------------------------------------------------------------ fetch

  private def log(line: String): Unit =
    if !Files.exists(LogFile) then
      Files.writeString(LogFile, "timestamp,site,date,variant,targets,consistent,absResolved,idResolved,tried\n")
    Files.writeString(LogFile, line + "\n", StandardOpenOption.APPEND)

  /**
   * With -Dregen.resume=true (used by scripts/regen_forever.sh): snapshots that already have a chromeDOM.html, or
   * that failed in two earlier runs, are skipped, so a restart after a block does not re-request them. Prints
   * "REMAINING <n>" after a pass that was not stopped by a block.
   */
  private val Resume = sys.props.get("regen.resume").contains("true")

  private def failures(site: String, date: String): Int =
    if !Files.exists(LogFile) then 0
    else Using.resource(scala.io.Source.fromFile(LogFile.toFile))(_.getLines().count(_.contains(s",$site,$date,FAIL,")))

  private def pending(site: String, date: String): Boolean =
    !Files.exists(Paths.get(s"$SitesDir/$site/$date/chromeDOM.html")) && failures(site, date) < 2

  def fetch(sites: Seq[String]): Unit =
    try
      for site <- sites; (date, url) <- snapshots(site) if !Resume || pending(site, date) do
        processSnapshot(site, date, url)
      if Resume then
        println(s"REMAINING ${sites.map(s => snapshots(s).count((d, _) => pending(s, d))).sum}")
    catch
      case e: BlockedByArchive =>
        println(s"STOPPED: ${e.getMessage}. Wait (hours) before resuming; finished snapshots are kept.")
        log(s"${LocalDateTime.now()},,,BLOCKED,,,,,${e.getMessage}")
    finally resetDrivers()

  private def processSnapshot(site: String, date: String, url: String): Unit =
    val out = Paths.get(s"$SitesDir/$site/$date/chromeDOM.html")
    val start = System.nanoTime()
    var best: Option[(String, Score, String)] = None // (variant, score, html)
    val tried = mutable.ListBuffer.empty[String]

    def consider(name: String, html: String, s: Score): Unit =
      tried += f"$name=${s.agree}/${s.targets}"
      if best.forall(b => Ordering[(Int, Int, Int)].gt(s.key, b._2.key)) then best = Some((name, s, html))

    // An existing file (from a previous run) is a candidate too; a good enough one is kept as is.
    if Files.exists(out) then
      val s = score(site, date, out)
      consider("existing", Files.readString(out), s)
      if s.fraction >= AlternateBelow then
        println(f"keep  $site $date existing ${s.agree}/${s.targets}")
        return

    val tmp = Files.createTempFile("chromeDOM", ".html")
    try
      val variants = variantsFor(site, date).iterator
      while variants.hasNext && !best.exists(_._2.fraction >= AlternateBelow) do
        val v = variants.next()
        renderWithRetry(url, v) match
          case Some(html) =>
            Files.writeString(tmp, html, StandardCharsets.UTF_8)
            consider(v.toString, html, score(site, date, tmp))
          case None => tried += s"$v=FAIL"
    finally Files.deleteIfExists(tmp)

    best match
      case Some((name, s, html)) =>
        if name != "existing" then Files.writeString(out, html, StandardCharsets.UTF_8)
        println(f"saved $site $date $name ${s.agree}/${s.targets} in ${(System.nanoTime() - start) / 1e9}%.0fs  [${tried.mkString(" ")}]")
        log(s"${LocalDateTime.now()},$site,$date,$name,${s.targets},${s.agree},${s.abs},${s.id},${tried.mkString(" ")}")
      case None =>
        println(s"FAIL  $site $date  [${tried.mkString(" ")}]")
        log(s"${LocalDateTime.now()},$site,$date,FAIL,,,,,${tried.mkString(" ")}")

  /** Renders a variant; on errors (crashed browser, rate limiting) restarts the browser and retries after a pause. */
  private def renderWithRetry(url: String, v: Variant): Option[String] =
    val result = (1 to 2).iterator.map { attempt =>
      val r = Try(render(url, v))
      r match
        case Failure(e: BlockedByArchive) => throw e // never retry into a block
        case Failure(e) =>
          println(s"      retry $attempt after ${e.getClass.getSimpleName}: ${Option(e.getMessage).map(_.linesIterator.next()).getOrElse("")}")
          resetDrivers()
          Thread.sleep(120_000L * attempt)
        case Success(_) => ()
      Thread.sleep(PauseBetweenPageLoadsMs) // every page load pulls many archived resources
      r
    }.collectFirst { case Success(html) => html }
    result

  // --------------------------------------------------------------- validate

  def validate(sites: Seq[String]): Unit =
    println("site,date,targets,absResolved,idResolved,idAgrees,tagAgrees")
    var tot = Score(0, 0, 0, 0, 0)
    var perfect, files = 0
    for site <- sites; (date, _) <- snapshots(site) do
      val dom = Paths.get(s"$SitesDir/$site/$date/chromeDOM.html")
      if !Files.exists(dom) then println(s"$site,$date,,missing,,,")
      else
        val s = score(site, date, dom)
        println(s"$site,$date,${s.targets},${s.abs},${s.id},${s.agree},${s.tag}")
        tot = Score(tot.targets + s.targets, tot.abs + s.abs, tot.id + s.id, tot.agree + s.agree, tot.tag + s.tag)
        files += 1
        if s.perfect then perfect += 1
    val n = tot.targets.max(1).toDouble
    println(f"TOTAL snapshots=$files perfect=$perfect targets=${tot.targets} absResolved=${tot.abs / n * 100}%.1f%% idResolved=${tot.id / n * 100}%.1f%% idAgrees=${tot.agree / n * 100}%.1f%% tagAgrees=${tot.tag / n * 100}%.1f%%")

  /** For each target: recorded absolute XPath vs the absolute path of the element its ID-XPath finds. */
  def diagnose(site: String, date: String): Unit =
    val dir = Paths.get(s"$SitesDir/$site/$date")
    val doc = Load.htmlToJDOM(dir.resolve("chromeDOM.html").toFile)
    val elements = Load.getDateElementsFor(dir).map(e => e.xpath.chrome -> e).toMap
    println("top-level body children: " + doc.getRootElement.getChildren.asScala
      .find(_.getName == "body").map(_.getChildren.asScala.map(c =>
        c.getName + Option(c.getAttributeValue("id")).map("#" + _).getOrElse("")).mkString(" ")).getOrElse("?"))
    for t <- targets(site).getOrElse(date, Seq.empty).take(8) do
      val actual = elements.get(t).toSeq
        .flatMap(e => Load.getJDOMElementsByXPath(e.xpath.idxpath, doc))
        .map(org.jdom2.xpath.XPathHelper.getAbsolutePath(_)).mkString(" | ")
      println(s"recorded $t\n  actual $actual")
