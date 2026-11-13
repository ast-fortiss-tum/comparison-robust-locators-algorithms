package elements

import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.syntax.*
import legacy.LegacyElementUtils
import org.openqa.selenium.WebElement
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.By
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}
import util.Utils.*
import java.time.Duration
import util.Load.getElementByXPath
import util.Load.htmlToJDOM
import scrape.SetupDrivers.initChromeDriver
import scrape.SetupDrivers.initFirefoxDriver
import scrape.LoadAllWebsites.tryLoadUrl
import util.Setup.chrome
import util.Setup.firefox
import util.Setup.firefox2
import java.util.concurrent.atomic.AtomicBoolean
import java.awt.Rectangle
import scala.jdk.CollectionConverters.*
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}
import robulanew.UtilsROBULAPlusNew
import robulanew.AlgorithmSettings
import robulanew.ElementInfo
import robulanew.XPathInfo
import robulanew.EuristicXPath
import robulanew.Utils
import org.jdom2.Document
import java.io.File
import org.jdom2.output.XMLOutputter
import org.jdom2.output.Format
import util.Load.getDateAndSiteFromArchiveUrl

case class Element(
  tag: String,
  innerText: Option[String],
  nodeText: Option[String],
  childrenLength: Int,
  neighbourText: Option[String] = None,
  xpath: Xpath,
  url: Option[String],
  cssSelector: String,
  location: (Int, Int),
  dimension: (Int, Int),
  attributes: Map[String, String],
  cssStyles: Map[String, String]
):

  def this(
    tag: String,
    innerText: Option[String],
    nodeText: Option[String],
    childrenLength: Int,
    xpath: Xpath,
    url: Option[String],
    cssSelector: String,
    location: (Int, Int),
    dimension: (Int, Int),
    attributes: Map[String, String],
    cssStyles: Map[String, String]
  ) = this(
    tag,
    innerText,
    nodeText,
    childrenLength,
    None,
    xpath,
    url,
    cssSelector,
    location,
    dimension,
    attributes.map((k, v) => (k, removeWayBackUrls(v))),
    cssStyles.map((k, v) => (k, removeWayBackUrls(v)))
  )

  override def equals(obj: Any): Boolean = obj match
    case that: Element =>
      tag == that.tag &&
      innerText == that.innerText &&
      nodeText == that.nodeText &&
      childrenLength == that.childrenLength &&
      url == that.url &&
      neighbourText == that.neighbourText &&
      xpath.chrome == that.xpath.chrome &&
      cssSelector == that.cssSelector &&
      location == that.location &&
      dimension == that.dimension &&
      attributes == that.attributes
    case _ => false

  def name: Option[String] = attributes.get("name")

  def id: Option[String] = attributes.get("id")

  def href: Option[String] = attributes.get("href")

  def title: Option[String] = attributes.get("title")

  def alt: Option[String] = attributes.get("alt")

  def area: Int = width * height

  def height: Int = dimension._2

  def width: Int = dimension._1

  def shape: Int = if height == 0 then 0 else (width * 100) / height

  def ariaLabel: Option[String] = attributes.get("aria-label")

  def isButton: Boolean =
    if tag == null then false
    else if tag.is("a") && className.indexOf("btn") >= 0 then true
    else if tag.is("button") then true
    else tag.is("input") && (typeName.is("button") || typeName.is("submit") || typeName.is("reset"))

  def className: Option[String] = attributes.get("class")

  def typeName: Option[String] = attributes.get("type")

  def addNeighborTexts(string: String): Element =
    copy(neighbourText = Some(string))

  def addNeighborTexts(strings: Seq[Element]): Element =
    LegacyElementUtils.addNeighborTexts(this, strings.asJava)

  def getVisibleText: Option[String] = Seq(innerText, value, placeholder).flatten.find(_.trim.nonEmpty)

  def value: Option[String] = attributes.get("value")

  def placeholder: Option[String] = attributes.get("placeholder")

  def getLocationArea: Rectangle = new Rectangle(x, y, width, height)

  def isNotEmpty: Boolean = !isEmpty

  def isEmpty: Boolean = xpath.chrome == "-"

  def json: String = this.asJson.spaces2

  def tolerantEquals(that: Element): Boolean =
    tag == that.tag &&
      nodeText == that.nodeText &&
      Math.abs(x - that.x) < 10 &&
      Math.abs(y - that.y) < 10 &&
      Math.abs(width - that.width) < 5 &&
      Math.abs(height - that.height) < 5 &&
      attributes == that.attributes

  def withNormalizedTag: Element = {
    this.copy(tag = this.tag.toLowerCase) // Normalizes the tag to lowercase
  }

  def y: Int = location._2

  def x: Int = location._1

object Element:

  private final val attributeExtractorScript = ressource("javascript/attribute_extractor.js").get
  private final val cssAttributesExtractorScript = ressource("javascript/css_attribute_extractor.js").get
  private final val xPathExtractorScript = ressource("javascript/xpath_extractor.js").get
  private final val cssSelectorExtractorScript = ressource("javascript/css_selector_extractor.js").get
  private final val directTextExtractorScript = ressource("javascript/direct_text_extractor.js").get
  private final val elementsExtractorScript = ressource("javascript/elements_extractor.js").get
  private final val elementFinderByXPathScript = ressource("javascript/element_finder_by_xpath.js").get

  private final val scriptsCombined = s"""
       |$attributeExtractorScript
       |$cssAttributesExtractorScript
       |$xPathExtractorScript
       |$cssSelectorExtractorScript
       |$directTextExtractorScript
       |$elementsExtractorScript
       |""".stripMargin

  private final val elementsExtractor = s"""
       |$scriptsCombined
       |return elements_extractor();
       |""".stripMargin

  private final val elementExtractor = s"""
        |$scriptsCombined
        |return element_extractor_string(arguments[0]);
        |""".stripMargin

  private final val robulaXPathExtractor = s"""
        |$xPathExtractorScript
        |return xpath_robulaplus_extractor(arguments[0]);
        |""".stripMargin

  private final val seleniumIDEXPathExtractor = s"""
       |$xPathExtractorScript
       |return xpath_selenium_ide_extractor(arguments[0]);
       |""".stripMargin

  private final val montotoXPathExtractor = s"""
       |$xPathExtractorScript
       |return xpath_montoto_extractor(arguments[0]);
       |""".stripMargin

  private final val elementGetterByXPath =
    s"""
       |$elementFinderByXPathScript
       |return getElementByXPath(arguments[0], document);
       |""".stripMargin

  def extractAll(driver: RemoteWebDriver): Seq[Element] =
    val elementsString = driver.executeScript(elementsExtractor).toString
    parseElements(elementsString)

  def getAbsoluteXPathFromJDOMElement(element: org.jdom2.Element): String =

    val abs: String = UtilsROBULAPlusNew.getAbsolutePathFromJDOMElement(element)
    abs


  def buildRobulaPlusXPath(document: Document, absXPath: String, url: String): List[String] =

    val site: String = getDateAndSiteFromArchiveUrl(url) match {
      case Some((date, siteName)) => s"$date/$siteName"
      case None => "unknown/unknown" // or any fallback default
    }
    // Get element metadata from absolute XPath
    val elementInfoFullPath = UtilsROBULAPlusNew.loadDataFullPath(document, absXPath, true, site)

    // Set up Robula+ configuration
    val settings = new AlgorithmSettings()
    settings.euristicSelection = false
    settings.specializeStars = true
    settings.powerSetAttributes = true
    settings.orderedInsert = true

    // Generate candidate XPath expressions using Robula+
    val candidateXPaths = UtilsROBULAPlusNew.robustXPathLocatorFinder(
      document,
      elementInfoFullPath,
      absXPath,
      url,
      settings
    )

    // Convert and return only the XPath strings
    candidateXPaths.asScala.map(_.getXpath).toList

  def getRobulaPlusXPath(driver: RemoteWebDriver, element: Element): String = {

    println("Finding element before robula+...")
    val webElementFound = driver.findElement(By.xpath(element.xpath.chrome))
    //    val webElementFound = chrome.executeScript(elementGetterByXPath, element.xpath.chrome).asInstanceOf[WebElement]
    println("Calculating XPath using Robula+...")
    val xpathRobula = driver.executeScript(robulaXPathExtractor, webElementFound).asInstanceOf[String]
    println("Finished calculating XPath using Robula+.")
    xpathRobula
  }

  def getMontotoXPath(driver: RemoteWebDriver, element: Element): String = {

    println("Finding element before montoto...")
    val webElementFound = driver.findElement(By.xpath(element.xpath.chrome))
    //    val webElementFound = chrome.executeScript(elementGetterByXPath, element.xpath.chrome).asInstanceOf[WebElement]
    println("Calculating XPath using montoto...")
    val xpathMontoto = driver.executeScript(montotoXPathExtractor, webElementFound).asInstanceOf[String]
    println("Finished calculating XPath using montoto.")
    xpathMontoto

  }

  def getSeleniumIDEXPath(driver: RemoteWebDriver, element: Element): String = {

    println("Finding element before seleniumIDE...")
    val webElementFound = driver.findElement(By.xpath(element.xpath.chrome))
    //    val webElementFound = chrome.executeScript(elementGetterByXPath, element.xpath.chrome).asInstanceOf[WebElement]
    println("Calculating XPath using seleniumIDE...")
    val xpathSeleniumIDE = driver.executeScript(seleniumIDEXPathExtractor, webElementFound).asInstanceOf[String]
    println("Finished calculating XPath using seleniumIDE.")
    xpathSeleniumIDE

  }

  def parseElements(string: String): Seq[Element] =
    decode[Seq[Element]](string).getOrElse(throw new Exception(s"Could not parsee elements: $string"))

  def extract(driver: RemoteWebDriver, element: WebElement): Element =
    val elementsString = driver.executeScript(elementExtractor, element).toString
    parseElement(elementsString)

  def parseElement(string: String): Element =
    decode[Element](string).getOrElse(throw new Exception(s"Could not parsee element: $string"))

  def empty: Element = Element(
    tag = "",
    nodeText = None,
    innerText = None,
    childrenLength = 0,
    xpath = Xpath.empty,
    url = None,
    cssSelector = "",
    location = (0, 0),
    dimension = (0, 0),
    attributes = Map.empty,
    cssStyles = Map.empty
  )

  def justTag(tag: String): Element = Element(
    tag = tag,
    nodeText = None,
    innerText = None,
    childrenLength = -1,
    xpath = Xpath("-", "-", "-"),
    url = None,
    cssSelector = "",
    location = (-1, -1),
    dimension = (-1, -1),
    attributes = Map.empty,
    cssStyles = Map.empty
  )
