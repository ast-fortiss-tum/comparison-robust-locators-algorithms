package eagl

import elements.Element
import similo.{Locator, State}
import util.Load.{getJDOMDocumentFromURL, getJDOMElementsByXPath}

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}
import upickle.default.*

case class EaglState(element: Element, xpath: Option[String]) extends State

/**
 * EAGL: Evolution-Aware Generation of robust Locators (Taha, Sohn, Papadakis, TOSEM 2026).
 *
 * The locators are generated offline with the authors' Python implementation (scripts/eagl_generate.py) and read
 * from cache/eagl_cache.json, keyed "<element url>|<element ID-XPath>" like the ROBULA+ cache. locate() evaluates the
 * XPath on the new version exactly like RobulaPlus: a candidate scores 1 if its ID-XPath resolves to an element the
 * EAGL locator selects.
 */
case class Eagl(cacheFile: String = "cache/eagl_cache.json") extends Locator:

  override type S = EaglState

  private lazy val cache: Map[String, String] =
    val path = Paths.get(cacheFile)
    if Files.exists(path) then read[Map[String, String]](new String(Files.readAllBytes(path), UTF_8))
    else Map.empty

  override def init(element: Element, elements: Seq[Element]): EaglState =
    val key = s"${element.url.getOrElse("")}|${element.xpath.idxpath}"
    EaglState(element, cache.get(key))

  override def locate(state: EaglState, elements: Seq[Element]): Seq[(Double, Element)] =
    val pageUrl = elements.headOption.flatMap(_.url)
    (state.xpath, pageUrl) match
      case (Some(xpath), Some(url)) =>
        val dom = getJDOMDocumentFromURL(url)
        val found = getJDOMElementsByXPath(xpath, dom)
        if found.isEmpty then elements.map(el => (0.0, el))
        else
          elements.map { el =>
            val candidate = getJDOMElementsByXPath(el.xpath.idxpath, dom).headOption
            (if candidate.exists(c => found.exists(_ eq c)) then 1.0 else 0.0, el)
          }.sortBy(-_._1)
      case _ => elements.map(el => (0.0, el)) // no locator generated for this element

  override def normalizer: Double = 1.0
