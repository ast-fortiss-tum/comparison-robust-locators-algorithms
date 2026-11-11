package sidereal

import elements.Element
import elements.Xpath
import org.jdom2.Document
import sidereal.{UtilsSIDEREAL, XPathInfo, ElementInfo, FragilityAnalyzer}
import similo.{Locator, State}
import util.Load.{getDateAndSiteFromArchiveUrl, getFilePathToChromeDOM, getJDOMDocumentFromURL}
import scala.jdk.CollectionConverters._
import java.time.LocalDate
import util.Setup.count

case class Sidereal() extends Locator:

  override type S = SiderealState

  // Fixed 4-month version dates
  val versions: Seq[LocalDate] = Seq(
    "2018-09-01", "2019-01-01", "2019-05-01", "2019-09-01",
    "2020-01-01", "2020-05-01", "2020-09-01", "2021-01-01",
    "2021-05-01", "2021-09-01", "2022-01-01", "2022-05-01",
    "2022-09-01", "2023-01-01", "2023-05-01", "2023-09-01"
  ).map(LocalDate.parse)

  override def init(element: Element, elements: Seq[Element]): SiderealState =

    val url = element.url.getOrElse("")
    val (dateStr, siteName) = getDateAndSiteFromArchiveUrl(url).getOrElse(("2020-01-01", "unknown"))
    val currentDate = LocalDate.parse(dateStr)
    val currentIndex = versions.indexOf(currentDate)
    val previousDateStr = if currentIndex > 0 then versions(currentIndex - 1).toString else currentDate.toString

    val document = getJDOMDocumentFromURL(url)
    val previousUrl = url.replace(dateStr, previousDateStr)
    val previousDOM = getJDOMDocumentFromURL(previousUrl)

    val site = s"$dateStr/$siteName"
    val elementInfoFullPath: java.util.ArrayList[ElementInfo] =
      sidereal.UtilsSIDEREAL.loadDataFullPath(document, element.xpath.idxpath).asInstanceOf[java.util.ArrayList[ElementInfo]]

    // Inject fragility weights
    val attrNames = List("id", "class", "name", "title", "alt", "value").asJava
    val fragilityWeights = FragilityAnalyzer.computeFragilityWeights(previousDOM, document, attrNames)
    UtilsSIDEREAL.fragilityWeights.clear()
    UtilsSIDEREAL.fragilityWeights.putAll(fragilityWeights)

    val siderealResults = UtilsSIDEREAL.siderealXPathLocatorFinder(
      document,
      document.getRootElement,
      elementInfoFullPath
    ).asScala.toList

    val bestXPath = siderealResults.headOption.map(_.getXpath).getOrElse(element.xpath.idxpath)
    count = count + 1
    println(count)
    SiderealState(element, xpath = bestXPath)

  override def locate(state: SiderealState, elements: Seq[Element]): Seq[(Double, Element)] =
    val pageUrl = elements.headOption.flatMap(_.url)
    val xpath = state.xpath
    val dom = getJDOMDocumentFromURL(pageUrl.getOrElse(""))

    pageUrl match
      case Some(url) =>
        val foundElements = util.Load.getJDOMElementsByXPath(xpath, dom)
        val result = elements.map { el =>
          val candidate = util.Load.getJDOMElementsByXPath(el.xpath.idxpath, dom).headOption
          val isMatch = candidate.exists(c => foundElements.exists(_ eq c))
          if isMatch then (1.0, el) else (0.0, el)
        }
        result.sortBy(-_._1)
      case None => elements.map(el => (0.0, el))

  override def normalizer: Double = 1.0

  def buildSiderealXPath(document: Document, absXPath: String, url: String): List[String] =
    val site = getDateAndSiteFromArchiveUrl(url) match
      case Some((date, siteName)) => s"$date/$siteName"
      case None => "unknown/unknown"

    val elementInfoFullPath = sidereal.UtilsSIDEREAL.loadDataFullPath(document, absXPath)
    val siderealXPaths = UtilsSIDEREAL.siderealXPathLocatorFinder(document, document.getRootElement, elementInfoFullPath)
    siderealXPaths.asScala.map(_.getXpath).toList

  def logTiming(site: Option[String], algorithm: String, xpath: String, timeMs: Double): Unit =
    val writer = new java.io.BufferedWriter(new java.io.FileWriter("timing_log.csv", true))
    writer.write(s"$site,$algorithm,$xpath,$timeMs\n")
    writer.close()
