package robulaplus

import elements.Element
import elements.Element.getRobulaPlusXPath
import elements.Element.buildRobulaPlusXPath
import elements.Xpath
import util.Setup.firefox
import util.Setup.firefox2
import util.Setup.count
import util.Setup.locked
import similo.{Locator, State}
import scrape.SetupDrivers
import scala.util.Try
import util.Load.getDateAndSiteFromArchiveUrl
import util.Load.getJDOMElementsByXPath
import util.Load.getFilePathToChromeDOM
import util.Load.getJDOMDocumentFromURL
import util.Setup.currentWebsiteFromVersionA
import util.Setup.currentWebsiteFromVersionB
import java.io.File
import java.time.LocalDate
import java.nio.file.Paths

import org.jdom2.Document
import org.jdom2.input.SAXBuilder
import scala.jdk.CollectionConverters._


//import robulanew.Utils
import robulanew.UtilsROBULAPlusNew

case class RobulaPlus() extends Locator:

  override type S = RobulaPlusState

  override def init(element: Element, elements: Seq[Element]): RobulaPlusState =
        val document = getJDOMDocumentFromURL(element.url.getOrElse(""))
        val startTime = System.nanoTime()
        // If the element cannot be resolved in the (regenerated) DOM, no locator is generated: an XPath that selects
        // nothing, so locate() scores every candidate 0 (same treatment as EAGL's failed generations).
        val firstXPath: String = Try(buildRobulaPlusXPath(document, element.xpath.idxpath, element.url.getOrElse("")).head)
          .getOrElse("/no-locator-generated")
        val endTime = System.nanoTime()
        val durationMs = (endTime - startTime) / 1_000_000.0
        logTiming(getDateAndSiteFromArchiveUrl(element.url.getOrElse("")).map(_._2), "RobulaPlus", element.xpath.chrome, durationMs)
        RobulaPlusState(element, xpathRobulaPlus = firstXPath)

  /**
   * Compare found element with r+ based on jdom2 Elements equality using each element xpath
   * @param state
   * @param elements
   * @return
   */
  override def locate(state: RobulaPlusState, elements: Seq[Element]): Seq[(Double, Element)] = {
    val pageUrl: Option[String] = elements.headOption.flatMap(_.url)
    val robulaXPath = state.xpathRobulaPlus
    val dom = getJDOMDocumentFromURL(pageUrl.getOrElse(""))

    val result: Seq[(Double, Element)] = pageUrl match {
      case Some(url) =>
        val foundElements = getJDOMElementsByXPath(robulaXPath, dom)

        if (foundElements.nonEmpty) {
          val scored = elements.map { el =>
            val maybeCandidate = getJDOMElementsByXPath(el.xpath.idxpath, dom).headOption

            val isMatch = maybeCandidate.exists { candidate =>
              foundElements.exists { found =>
                candidate eq found
              }
            }

            if (isMatch) {
              // println(s"[DEBUG] MATCH: $robulaXPath ↔ ${el.xpath.chrome}")
              (1.0, el)
            } else {
              (0.0, el)
            }
          }

          val allZero = scored.forall { case (score, _) => score == 0.0 }
          if (allZero) {
          }
          scored.sortBy(-_._1)
        } else {
          elements.map(el => (0.0, el))
        }

      case None =>
        println(count)
        elements.map(el => (0.0, el))
    }

    result
  }

  def getRobulaXPathFromCache(element: Element): String =
    val url     = element.url.getOrElse("")
    val ID_xpath = element.xpath.idxpath

    // If we have no URL just return the idxpath.
    if (url.isEmpty) return ID_xpath
    val key = s"$url|$ID_xpath"

    RobulaCache.getOrCompute(key) {
      val document   = getJDOMDocumentFromURL(url)
      val candidates = buildRobulaPlusXPath(document, ID_xpath, url)
      candidates.headOption.getOrElse(ID_xpath)
    }

  override def normalizer: Double = 1.0
//  /**
//   * Compare found element with r+ and the one in elements with absolute / idxpath
//   * @param state
//   * @param elements
//   * @return
//   */
//  override def locate(state: RobulaPlusState, elements: Seq[Element]): Seq[(Double, Element)] = {
//    val pageUrl: Option[String] = elements.headOption.flatMap(_.url)
//    val robulaXPath = state.xpathRobulaPlus
//    //    count += 1
//    //    println(count)
//    val result: Seq[(Double, Element)] = pageUrl match {
//      case Some(url) =>
//        getJDOMElementByXPath(robulaXPath, url) match {
//          case Some(found) =>
//            if (!elements.exists(_.xpath.chrome == found)) {
//              //              println(s"[DEBUG] NOT FOUND in elements: $found and r+: $robulaXPath and site: ${getDateAndSiteFromArchiveUrl(url)}")
//            }
//            elements.map { el =>
//                if (el.xpath.chrome == found) {
//                  //                  println(s"[DEBUG] FOUND in elements: $found and r+: $robulaXPath and site: ${getDateAndSiteFromArchiveUrl(url)}")
//                  (1.0, el)
//                } else (0.0, el)
//              }
//              .sortBy(-_._1) // Sort descending by score
//
//          case None =>
//            //            println(s"[DEBUG] getElementByXPath returned None for xpath: $robulaXPath and url: $url")
//            elements.map(el => (0.0, el))
//        }
//
//      case None =>
//        println(s"[DEBUG] No page URL available in elements.")
//        elements.map(el => (0.0, el))
//    }
//    result
//  }

  def logTiming(site: Option[String], algorithm: String, xpath: String, timeMs: Double): Unit =
    val dir = java.nio.file.Paths.get("results", "runtime", algorithm)
    java.nio.file.Files.createDirectories(dir)
    val logFile = dir.resolve("timing_log_1.csv").toFile
    val writer = new java.io.BufferedWriter(new java.io.FileWriter(logFile, true))
    writer.write(s"$site,$algorithm,$xpath,$timeMs\n")
    writer.close()