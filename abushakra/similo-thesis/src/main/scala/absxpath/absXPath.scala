package absxpath

import similo.Locator
import elements.Element as MyElement
import util.Load
import robulanew.UtilsROBULAPlusNew
import org.jdom2.{Document, Element}
import org.jdom2.xpath.XPathFactory
import org.jdom2.filter.Filters

import java.io.File
import scala.jdk.CollectionConverters.*
import scala.util.Try

final case class absXPath() extends Locator {
  override type S = absXPathState

  override def init(element: MyElement, elements: Seq[MyElement]): absXPathState = {
    val doc     = Load.getJDOMDocumentFromURL(element.url.getOrElse(""))
    val source  = Load.getJDOMElementsByXPath(element.xpath.idxpath, doc).head
    val absPath = UtilsROBULAPlusNew.getAbsolutePathFromJDOMElement(source)
    absXPathState(element, absPath)
  }

  override def locate(state: absXPathState, elements: Seq[MyElement]): Seq[(Double, MyElement)] = {
    val pageUrl  = elements.headOption.flatMap(_.url).getOrElse("")
    val domPath  = Load.getFilePathToChromeDOM(pageUrl)
    val dom      = Load.htmlToJDOM(new File(domPath))

    applyUnique(state.xpath, dom) match {
      case Some(winner) => scoreAgainst(elements, dom, winner)
      case None         => elements.map(el => (0.0, el))
    }
  }

  override def normalizer: Double = 1.0

  private def sanitizeXPath(x: String): String =
    if x != null && x.contains("||") then
      // Attempt to repair patterns like //*[@id=' || 'value' ']
      val Pattern = ".*@id\\s*=\\s*'?\\s*\\|\\|\\s*'([^']+)'.*".r
      x match
        case Pattern(id) => s"//*[@id='${id}']"
        case _           => x
    else x

  private def compileSafe(xpath: String): Option[org.jdom2.xpath.XPathExpression[org.jdom2.Element]] =
    val cleaned = sanitizeXPath(xpath)
    Try(XPathFactory.instance().compile(cleaned, Filters.element())).toOption

  private def applyUnique(xpath: String, dom: Document): Option[Element] =
    compileSafe(xpath) match
      case Some(expr) =>
        val result = expr.evaluate(dom).asScala
        if result.size == 1 then Some(result.head) else None
      case None =>
        println(s"[DEBUG][absXPath] XPath compile failed -> ${xpath}")
        None

  private def scoreAgainst(elements: Seq[MyElement], dom: Document, winner: Element): Seq[(Double, MyElement)] =
    val winnerRef = winner
    elements
      .map { el =>
        val xp = sanitizeXPath(el.xpath.idxpath)
        val cand = compileSafe(xp).flatMap(_.evaluate(dom).asScala.headOption)
        val isSame = cand.exists(_ eq winnerRef)
        (if isSame then 1.0 else 0.0, el)
      }
      .sortBy(-_._1)
}