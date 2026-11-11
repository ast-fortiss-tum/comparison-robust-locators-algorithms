package util

import com.github.benmanes.caffeine.cache.LoadingCache
import elements.Element as MyElement
import elements.Element.parseElements
import elements.Element.parseElement
import elements.Element.extract
import scrape.SetupDrivers.initChromeDriver
import scrape.SetupDrivers.initFirefoxDriver
import evalutation.Classification
import scrape.Site
import util.Setup.locked2
import util.Setup.locked
import util.Setup.firefox
import util.Setup.firefox2
import util.Setup.currentWebsiteFromVersionA
import util.Setup.currentWebsiteFromVersionB
import util.Utils.{read, walk}
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.syntax.*
import legacy.LegacyElementUtils
import org.openqa.selenium.WebElement
import org.openqa.selenium.By
import org.openqa.selenium.remote.RemoteWebDriver
import util.Utils.*
import java.time.Duration
import util.Setup.chrome
import evalutation.Classification
import evalutation.Classification.ElementClassification.*
import evalutation.Classification.LocatorClassification.*
import vista.VistaCache

import java.awt.Rectangle
import scala.jdk.CollectionConverters.*
import scala.io.Source

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.time.LocalDate

import org.jsoup.Jsoup
import org.jsoup.nodes.Document.OutputSettings
import org.jsoup.nodes.Entities
import java.io._
import org.jdom2.input.SAXBuilder
import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.xpath.{XPathFactory, XPathHelper, XPathExpression}
import org.jdom2.filter.Filters
import sys.process._

object Load:

  val crossValidate: Seq[Path] = Seq(
    // "sites/zz_aol",
    "sites/zz_cnn",
    "sites/zz_foxnews",
    "sites/zz_instagram",
    // "sites/zz_imdb"
    "sites/zz_fandom"
  ).map(Path.of(_))
  private val elementsCache: LoadingCache[Path, Seq[MyElement]] =
    Utils.createCache((path: Path) => getDateElementsForFromSystem(path))
  private val elementsDateCache: LoadingCache[Path, Seq[(LocalDate, Seq[MyElement])]] =
    Utils.createCache((path: Path) => getAllElementsForFromSystem(path))

  private final val elementFinderByXPathScript = ressource("javascript/element_finder_by_xpath.js").get
  private final val elementsExtractorScript = ressource("javascript/elements_extractor.js").get
  private final val elementGetterByXPath = s"""
       |$elementFinderByXPathScript
       |return getElementByXPath(arguments[0], document);
       |""".stripMargin

  def getDatesFor(site: Path): Seq[LocalDate] =
    walk(site)
      .filter(Files.isDirectory(_))
      .map(_.toString.split(File.separatorChar).last)
      .map(LocalDate.parse(_))
      .sorted

  def getDateAndSiteFromArchiveUrl(archiveUrl: String, sitesDir: String = "sites"): Option[(String, String)] =

    // Helper: extract site name from archive URL
    def extractSiteName(url: String): Option[String] =
      val pattern = """https?://web\.archive\.org/web/\d+(if_)?/(https?:\/\/)?([^/]+).*""".r
      url match
        case pattern(_, _, domain) =>
          val baseDomain = domain.toLowerCase.split("\\.").filter(_.nonEmpty)
          val secondLevel = if baseDomain.length >= 2 then baseDomain(baseDomain.length - 2) else domain
          secondLevel match
            case "discordapp" => Some("discord")
            case other => Some(other)
        case _ => None

    // Step 1: extract site name
    val maybeSite = extractSiteName(archiveUrl)
    if maybeSite.isEmpty then return None
    val siteName = maybeSite.get

    // Step 2: build path to urls.csv
    val urlsCsvPath = Paths.get(s"$sitesDir/$siteName/urls.csv")
    if !Files.exists(urlsCsvPath) then return None

    // Step 3: look for matching line
    val lines = scala.util.Using.resource(Source.fromFile(urlsCsvPath.toFile))(_.getLines().drop(1).toList)  // close the file (was leaking one descriptor per call)

    lines
      .map(_.split(";", 2))
      .collect {
        case Array(date, url) if url.stripSuffix("/") == archiveUrl.stripSuffix("/") =>
          (date.trim, siteName)
      }
      .headOption

  def getFilePathToChromeDOM(url: String): String =

    val dateAndSiteName = getDateAndSiteFromArchiveUrl(url)

    val absPathToDOM = dateAndSiteName match
      case Some((date, siteName)) =>
        val domPath = Paths.get("sites", siteName, date, "chromeDOM.html").toAbsolutePath.toString
        domPath
      case None =>
        println(s"Could not extract date and site from URL: $url")
        ""

    val filePath = absPathToDOM
    filePath

  def getFilePathToScreenshot(url: String): String =

    val dateAndSiteName = getDateAndSiteFromArchiveUrl(url)

    val absPathToScreenshot = dateAndSiteName match
      case Some((date, siteName)) =>
        val domPath = Paths.get("sites", siteName, date, "screenshot.png").toAbsolutePath.toString
        domPath
      case None =>
        println(s"Could not extract date and site from URL: $url")
        ""

    val filePath = absPathToScreenshot
    filePath

  def getFilePathToTemplates(url: String, element: MyElement): String =

    val dateAndSiteName = getDateAndSiteFromArchiveUrl(url)

    val absPathToTemplates = dateAndSiteName match
      case Some((date, siteName)) =>
        val customPath = s"${siteName}_${date}_${element.tag}_${element.x}_${element.y}.png"
        val domPath = Paths.get("sites", "templates", customPath).toAbsolutePath.toString
        domPath
      case None =>
        println(s"Could not extract date and site from URL: $url")
        ""

    val filePath = absPathToTemplates
    filePath

  def htmlToJDOM(file: File): Document = {
    val jsoupDoc = Jsoup.parse(file, "UTF-8")

    // Configure output for well-formed XHTML
    jsoupDoc.outputSettings()
      .syntax(OutputSettings.Syntax.xml)
      .escapeMode(Entities.EscapeMode.xhtml)
      .prettyPrint(false)
      .charset("UTF-8")

    // Remove attributes with ":"
    jsoupDoc.getAllElements.forEach { el =>
      el.attributes().asList().forEach { attr =>
        if (attr.getKey.contains(":")) {
          el.removeAttr(attr.getKey)
        }
      }
    }

    // Remove elements with prefixed tag names (e.g., csi:vars)
    jsoupDoc.select("*").forEach { el =>
      if (el.tagName().contains(":")) el.remove()
    }

    // Remove <script> and <style> tags
    jsoupDoc.select("script, style").forEach(_.remove())

    // Remove invalid comments containing "--" from amazon DOM trees
    jsoupDoc.getAllElements.asScala.foreach { el =>
      el.childNodes().asScala.collect {
        case c: org.jsoup.nodes.Comment if c.getData.contains("--") => c
      }.foreach(_.remove())
    }

    // Sanitize CDATA closers and strip DOCTYPE
    val cleanedXhtml = jsoupDoc.html()
      .replace("]]>", "]]&gt;")
      .replaceFirst("(?i)<!DOCTYPE[^>]*>", "")

    val inputStream = new ByteArrayInputStream(cleanedXhtml.getBytes("UTF-8"))

    val builder = new SAXBuilder()
    builder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    builder.setFeature("http://xml.org/sax/features/validation", false)

    builder.build(inputStream)
  }

  /**
   * Lookup the element Classification for a specific element at a specific archived page version,
   * by matching its absolute XPath against sites/<site>/xpaths.csv and pairing it with the same
   * row from sites/<site>/classified.csv for that date.
   */
  def classificationForElementAt(archiveUrl: String, absXPath: String): Classification = {
    classificationAndLocatorForElementAt(archiveUrl, absXPath)
      .map(_._1)
      .getOrElse(Classification(evalutation.Classification.ElementClassification.DELETED,
        evalutation.Classification.LocatorClassification.DELETED)) // fallback if not found
  }

  /** Same as above, but also returns the locator-change label from classified.csv (second column). */
  def classificationAndLocatorForElementAt(
                                            archiveUrl: String,
                                            absXPath: String
                                          ): Option[(Classification, String)] = {
    getDateAndSiteFromArchiveUrl(archiveUrl) match {
      case Some((dateStr, siteName)) =>
        val sitePath: Path = Path.of("sites", siteName)
        val date           = LocalDate.parse(dateStr)

        // Load: date -> Seq[xpath]
        val xpathsByDate: Map[LocalDate, Seq[String]] =
          getXPathsFor(sitePath).toMap

        // Load: date -> Seq[(Classification, locatorChangeLabel)]
        val classesByDate: Map[LocalDate, Seq[(Classification, String)]] =
          getClassificationFor(sitePath).toMap

        for {
          xpathsForDate <- xpathsByDate.get(date)
          idx           <- Some(xpathsForDate.indexOf(absXPath)).filter(_ >= 0)
          classesForDate<- classesByDate.get(date)
          clsTuple      <- classesForDate.lift(idx) // (Classification, locatorChangeLabel)
        } yield clsTuple

      case None =>
        None
    }
  }

  def getClassificationFor(site: Path): Seq[(LocalDate, Seq[(Classification, String)])] =
    val path = ensureFileName(site, "classified.csv")
    val csv = Csv(path)
    csv.columns.columns.map: column =>
      (LocalDate.parse(column.header), column.values.map(Classification.load))

  private def ensureFileName(path: Path, shouldEndWith: String): Path =
    val startsSite = if path.startsWith("sites") then path else Path.of("sites").resolve(path)
    if startsSite.endsWith(shouldEndWith) then startsSite
    else startsSite.resolve(shouldEndWith)

  def getXPathsFor(site: String): Seq[(LocalDate, Seq[String])] =
    Load.getXPathsFor(Path.of(site))

  def getXPathsFor(site: Path): Seq[(LocalDate, Seq[String])] =
    val path = ensureFileName(site, "xpaths.csv")
    val csv = Csv(path)
    csv.columns.columns.map: column =>
      (LocalDate.parse(column.header), column.values)

  def getFullLoadedSuitableSites: Seq[Path] =
    Load.getSitesFor.filter: site =>
      Files.exists(site.resolve("xpaths.csv")) &&
        Files.exists(site.resolve("classified.csv"))

  def getSitesFor: Seq[Path] =
    walk("sites")
      .filter(Files.isDirectory(_))
      .filter(_.getParent.endsWith("sites"))
      .filterNot(_.getFileName.toString.startsWith("zz_"))
      .filterNot(_.getFileName.toString.startsWith("."))

  def getUrlsFor(path: Path): Map[LocalDate, String] =
    val full = ensureFileName(path, "urls.csv")
    if Files.notExists(full) then
      throw new Exception(s"File $full does not exist!")
    else
      read(full)
        .split("\n")
        .tail
        .filter(_.nonEmpty)
        .map: line =>
          line.split(";").toList match
            case date :: url :: Nil =>
              LocalDate.parse(date) -> url
            case bad =>
              throw new Exception(s"Invalid line format: $line (split into: $bad)")
        .toMap

  /**
   * Try to locate an element in a dom tree using xpath expression
   * @param xpath The xpath expression you want to find the element with
   * @param url Element url where the dom will be extracted
   * @return The JDOM Element found
   */
  def getJDOMElementsByXPath(xpath: String, dom: Document): Seq[Element] = {
    try {
      val xPathExpr: XPathExpression[Element] =
        XPathFactory.instance().compile(xpath, Filters.element())

      val elements = xPathExpr.evaluate(dom).asScala.toSeq

      if (elements.size > 1) {
//        println(s"[DEBUG] XPath matched ${elements.size} elements on xpath: $xpath")
      } else if (elements.isEmpty) {
//        println(s"[DEBUG] No elements found for XPath: $xpath")
      }

      elements
    } catch {
      case e: Exception =>
//        println(s"[ERROR] Exception while evaluating XPath '$xpath': ${e.getMessage}")
        Seq.empty
    }
  }

  /**
   * Parse an element url to a JDOM Document from that url
   * @param url Element url
   * @return DOM HTML file
   */
  def getJDOMDocumentFromURL(url: String): Document =
    val htmlPath = getFilePathToChromeDOM(url)
    val file = new File(htmlPath)

    // Parse HTML file as XHTML-compliant JDOM Document
    val document = htmlToJDOM(file)
    document

  /**
   * Try to locate an element in a dom tree using xpath expression
   * @param xpath The xpath expression you want to find the element with
   * @param url Element url where the dom will be extracted
   * @return The absolute XPath of the element found
   */
  def getElementByXPath(xpath: String, url: String): Option[String] = {

    try {
//      println(s"🔍 Loading file for: $url")
      val htmlPath = getFilePathToChromeDOM(url)
      val file = new File(htmlPath)

      // Parse HTML file as XHTML-compliant JDOM Document
      val document = htmlToJDOM(file) // If you already have this, use it

      // Evaluate XPath
      val xPathExpr: XPathExpression[Element] =
        XPathFactory.instance().compile(xpath, Filters.element())
      val elements = xPathExpr.evaluate(document).asScala
      val firstElement: Option[Element] = elements.headOption
      val absPath = firstElement.map(el => XPathHelper.getAbsolutePath(el).toString)

      if (elements.nonEmpty) {
//        println(s"✅ Found element with an absolute XPath: $absPath")
        absPath
      } else {
//        println(s"❌ No element found for XPath: $xpath")
        None
      }

    } catch {
      case e: Exception =>
//        println(s"💥 Error while processing $url with XPath $xpath: ${e.getMessage}")
        None
    }

//    try
//      println(s"🔍 Navigating to: $url")
//      val htmlPath = getFilePathToChromeDOM(url)
//      if(currentWebsiteFromVersionB == ""){
//        firefox2.get(htmlPath)
//        currentWebsiteFromVersionB = url
//      }else if(currentWebsiteFromVersionB == url){
//        //do nothing
//      }else{
//        firefox2.get(htmlPath)
//        currentWebsiteFromVersionB = url
//      }
//
//      val matches = firefox2.findElements(By.xpath(xpath))
//
//      if (!matches.isEmpty) {
//        println(s"✅ Element successfully found using XPath: $xpath")
//        Some(extract(firefox2, matches.get(0)))
//      } else {
//        println(s"❌ Element not found for XPath: $xpath")
//        None
//      }
//
//    catch
//      case e: Exception =>
//        println(s"💥 Unexpected error while processing $url with XPath $xpath: ${e.getMessage}")
//        None
//
//    finally
////      locked = false
//      ()
  }

  def getAllElementsByDateFor(path: String): Seq[(LocalDate, Seq[MyElement])] =
    getAllElementsByDateFor(Path.of(path))

  def getAllElementsByElementFor(path: String): Seq[Seq[(LocalDate, MyElement)]] =
    getAllElementsByElementFor(Path.of(path))

  def getAllElementsByElementFor(path: Path): Seq[Seq[(LocalDate, MyElement)]] =
    Utils.reverseSequence(getAllElementsByDateFor(path).map(t => t._2.map(e => (t._1, e))))

  def getAllElementsByDateFor(path: Path): Seq[(LocalDate, Seq[MyElement])] =
    elementsDateCache.get(path)

  def getDateElementsFor(path: String): Seq[MyElement] = Load.getDateElementsFor(Path.of(path))

  def getDateElementsFor(path: Path): Seq[MyElement] = elementsCache.get(path)

  def getSitesFromFile: Seq[Site] =
    val sites = read(Path.of("sites/sites.txt"))
    sites.split("\n").map(Site(_)).toSeq

  private def getAllElementsForFromSystem(path: Path): Seq[(LocalDate, Seq[MyElement])] = {

    val totalPath =
      if path.startsWith("sites") || path.startsWith("src") then path
      else Path.of("sites").resolve(path)
    walk(totalPath)
      .filter(Files.isDirectory(_))
      .map((dir: Path) => (dateFromPath(dir), getDateElementsFor(dir)))
      .sortBy(_._1)

  }

  private def dateFromPath(path: Path): LocalDate =
    LocalDate.parse(path.getFileName.toString)

  private def getDateElementsForFromSystem(path: Path): Seq[MyElement] = {
    val elements = read(path.resolve("elements.json"))
    parseElements(elements)
  }

  /** Returns best-match (x,y) for (screenshotB, template), using the on-disk cache. */
  def getMatchPointFromCache(screenshotBPath: String, templatePath: String): Option[(Int, Int)] = {
    if (screenshotBPath.isEmpty || templatePath.isEmpty) return None
    val key = s"$screenshotBPath|$templatePath"

    try {
      // Only cache when a match is actually found; otherwise propagate a miss.
      val pt: (Int, Int) = VistaCache.getOrCompute(key) {
        matchTemplate(screenshotBPath, templatePath).getOrElse {
          throw new NoSuchElementException(s"Vista match not found for key: $key")
        }
      }
      Some(pt)
    } catch {
      case _: NoSuchElementException =>
        // No match: do not cache; surface as None to the caller.
        None
    }
  }

  // Step 1: Extract first 10 initial XPaths from classified.csv
   def getFirst10InitialXPaths(classifiedPath: String): Seq[String] = {
    val lines = Source.fromFile(classifiedPath).getLines().toSeq
    // Skip header (first line with dates)
    val rows = lines.drop(1)

    rows.take(10).flatMap { row =>
      val parts = row.split(";").toSeq
      // First column looks like "initial|initial|/html/body/..."
      val initialCol = parts.headOption.getOrElse("")
      val xpath = initialCol.split("\\|").lift(2).getOrElse("")
      if (xpath.nonEmpty && xpath != "-") Some(xpath) else None
    }
  }

  // Step 2: Filter elements.json by those XPaths
   def getFirst10ElementsFromSite(siteDir: String): Seq[MyElement] = {
    val classifiedPath = Paths.get(siteDir, "classified.csv")
    // elements.json is specifically in <siteDir>/2018-09-01/elements.json
    val elementPath    = Paths.get(siteDir, "2018-09-01", "elements.json")

    val allElements = parseElements(read(elementPath))
    val first10XPaths = getFirst10InitialXPaths(classifiedPath.toString).toSet

    allElements.filter(e => first10XPaths.contains(e.xpath.chrome))
  }

  private def inferScale(imgW: Int, imgH: Int): Double =
    // If you recorded coordinates at a different logical width, override at runtime:
    // sbt -Dvista.logicalWidth=1366 run
    val logicalW = sys.props.get("vista.logicalWidth").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(1920)
    val raw      = imgW.toDouble / logicalW.toDouble
    // Snap to common DPRs so 1.999 becomes 2.0, etc.
    val common   = Array(0.75, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0)
    common.minBy(d => math.abs(d - raw))


  def cropTemplate(screenshot: String, output: String, x: Int, y: Int, w: Int, h: Int): Boolean = {
    val img = javax.imageio.ImageIO.read(new java.io.File(screenshot))
    if (img == null) {
      println(s"[ERROR] Could not read image: $screenshot")
      return false
    }

    // Normalize negative dimensions (make rect always “down-right”)
    var x0 = x
    var y0 = y
    var w0 = w
    var h0 = h
    if (w0 < 0) { x0 = x0 + w0; w0 = -w0 }
    if (h0 < 0) { y0 = y0 + h0; h0 = -h0 }

    // Scale from logical coords → pixel coords
    val scale = inferScale(img.getWidth, img.getHeight)
    var X = (x0 * scale).toInt
    var Y = (y0 * scale).toInt
    var W = (w0 * scale).toInt
    var H = (h0 * scale).toInt

    // 3) If zero or tiny, inflate to a small window (treat as point)
    val minW = 12
    val minH = 12
    if (W <= 0) { W = minW; X = X - W / 2 }
    if (H <= 0) { H = minH; Y = Y - H / 2 }

    // Clamp to image bounds
    X = X.max(0).min(img.getWidth  - 1)
    Y = Y.max(0).min(img.getHeight - 1)
    W = W.max(0).min(img.getWidth  - X)
    H = H.max(0).min(img.getHeight - Y)

    if (W <= 0 || H <= 0) {
      println(s"[ERROR] Crop region invalid after clamping: X=$X($x0), Y=$Y($y0), W=$W($w0), H=$H($h0) scale=$scale (image=${img.getWidth}x${img.getHeight}): $screenshot")
      return false
    }

    val cmd = Seq("python3", "scripts/crop_element.py", screenshot, output, X.toString, Y.toString, W.toString, H.toString)
    import sys.process._
    cmd.! == 0
  }

  def matchTemplate(fullImg: String, templateImg: String): Option[(Int, Int)] = {
    import sys.process._
    val cmd = Seq("python3", "scripts/visual_match.py", fullImg, templateImg)
    try {
      val out = cmd.!!
      val Array(x, y) = out.trim.split(" ").map(_.toInt)
      val scale = 2.0
      Some((x / scale).toInt, (y / scale).toInt)
    } catch {
      case _: Exception => None
    }
  }

