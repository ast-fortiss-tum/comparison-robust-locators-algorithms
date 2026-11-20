package siderealnew

import de.tum.ls4.generators.{AncestorNode, Ancestry, FragilityModel, Sidereal as SiderealGenerator, XPathOracle}
import elements.Element
import org.jdom2.{Document, Element as JElement, Text}
import similo.{Locator, State}
import util.Load
import util.Load.{getDateAndSiteFromArchiveUrl, getJDOMDocumentFromURL, getJDOMElementsByXPath}

import java.io.File
import java.nio.file.{Files, Paths}
import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters.*
import scala.util.Using

case class SiderealNewState(element: Element, xpath: Option[String]) extends State

/**
 * SIDEREAL (Leotta, Ricca, Tonella, STVR 2021), using the faithful re-implementation of the Similocator library
 * (de.tum.ls4.generators, lib/similocator-*.jar).
 *
 * Locators for version n are generated with fragility weights learned from versions n-1 -> n of the same site, using
 * the tracked elements of xpaths.csv as the old->new mapping (each row is the same element across dates), exactly the
 * information the paper obtained from its manual mapping. The first version has no history: default weights (0.5).
 * locate() evaluates the XPath on the new version like RobulaPlus.
 *
 * @param useHistory false = uniform weights everywhere (the paper's RQ4 baseline).
 */
case class SiderealNew(useHistory: Boolean = true) extends Locator:

  override type S = SiderealNewState

  override def init(element: Element, elements: Seq[Element]): SiderealNewState =
    val url = element.url.getOrElse("")
    val xpath = for
      (date, site) <- getDateAndSiteFromArchiveUrl(url)
      doc = getJDOMDocumentFromURL(url)
      target <- getJDOMElementsByXPath(element.xpath.idxpath, doc).headOption
    yield
      val model = if useHistory then SiderealNew.weights(site, date) else FragilityModel.empty()
      new SiderealGenerator(model).generate(SiderealNew.ancestry(target), SiderealNew.oracle(doc, target)).xpath()
    SiderealNewState(element, xpath)

  override def locate(state: SiderealNewState, elements: Seq[Element]): Seq[(Double, Element)] =
    (state.xpath, elements.headOption.flatMap(_.url)) match
      case (Some(xpath), Some(url)) =>
        val dom = getJDOMDocumentFromURL(url)
        val found = getJDOMElementsByXPath(xpath, dom)
        if found.isEmpty then elements.map(el => (0.0, el))
        else
          elements.map { el =>
            val candidate = getJDOMElementsByXPath(el.xpath.idxpath, dom).headOption
            (if candidate.exists(c => found.exists(_ eq c)) then 1.0 else 0.0, el)
          }.sortBy(-_._1)
      case _ => elements.map(el => (0.0, el))

  override def normalizer: Double = 1.0

object SiderealNew:

  /** The ancestor chain of a JDOM element, as the library's ancestry.js extracts it from a live DOM. */
  def ancestry(element: JElement): Ancestry =
    val nodes = Iterator.iterate(element)(_.getParentElement).takeWhile(_ != null).map { el =>
      val siblings = Option(el.getParentElement).map(_.getChildren.asScala.toSeq).getOrElse(Seq(el))
      val before = siblings.takeWhile(_ ne el)
      val attributes = new java.util.LinkedHashMap[String, String]()
      el.getAttributes.asScala.foreach(a => attributes.put(a.getName, a.getValue))
      val text = el.getContent.asScala.collectFirst { case t: Text => t.getText }.orNull
      new AncestorNode(el.getName, before.count(_.getName == el.getName) + 1, before.size + 1, attributes, text)
    }.toList
    new Ancestry(nodes.asJava)

  /** Uniqueness checks against the JDOM document, the same XPath engine the evaluation uses. */
  def oracle(doc: Document, target: JElement): XPathOracle = xpaths =>
    xpaths.asScala.map { x =>
      val found = getJDOMElementsByXPath(x, doc)
      new XPathOracle.Match(found.size, found.exists(_ eq target))
    }.asJava

  private val weightsCache = TrieMap.empty[(String, String), FragilityModel]

  /** Fragility weights for generating locators on (site, date), learned from the previous version of the site. */
  def weights(site: String, date: String): FragilityModel =
    weightsCache.getOrElseUpdate((site, date), learn(site, date))

  private def dom(site: String, date: String): Option[Document] =
    val f = new File(s"sites/$site/$date/chromeDOM.html")
    Option.when(f.exists())(Load.htmlToJDOM(f))

  private def learn(site: String, date: String): FragilityModel =
    val lines = Using.resource(scala.io.Source.fromFile(s"sites/$site/xpaths.csv"))(_.getLines().toList)
    val dates = lines.head.split(";").map(_.trim)
    val i = dates.indexOf(date)
    if i <= 0 then FragilityModel.empty()
    else
      val prevDate = dates(i - 1)
      (dom(site, prevDate), dom(site, date)) match
        case (Some(prev), Some(curr)) =>
          val mappings = lines.tail.filter(_.nonEmpty).map(_.split(";", -1).map(_.trim)).flatMap { row =>
            for
              a <- row.lift(i - 1).filter(_.startsWith("/"))
              b <- row.lift(i).filter(_.startsWith("/"))
              ea <- getJDOMElementsByXPath(a, prev).headOption
              eb <- getJDOMElementsByXPath(b, curr).headOption
            yield new FragilityModel.Mapping(ancestry(ea), ancestry(eb))
          }
          FragilityModel.empty().learn(mappings.asJava)
        case _ => FragilityModel.empty()
