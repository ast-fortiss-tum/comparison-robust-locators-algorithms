package multilocator

import similo.{Locator, State}
import elements.Element as MyElement
import util.Load
import util.Setup.count
import elements.Element.buildRobulaPlusXPath
import elements.Element.getSeleniumIDEXPath
import elements.Element.getMontotoXPath
import util.Load.getDateAndSiteFromArchiveUrl
import util.Load.getElementByXPath
import util.Load.getFilePathToChromeDOM
import util.Load.getJDOMDocumentFromURL
import util.Load.getJDOMElementsByXPath
import util.Load.{getFilePathToScreenshot, getFilePathToTemplates, cropTemplate, matchTemplate, getJDOMElementsByXPath, getMatchPointFromCache}
import robulanew.UtilsROBULAPlusNew
import robulanew.AlgorithmSettings
import robulanew.ElementInfo
import robulanew.XPathInfo
import robulanew.EuristicXPath
import robulanew.Utils
import robulanew.XPathGeneratorUtil

import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.xpath.XPathFactory
import org.jdom2.filter.Filters

import scala.jdk.CollectionConverters.*
import java.io.File

case class MultiLocator(withRepair: Boolean = false, withVista: Boolean = false) extends Locator:

  override type S = MultiLocatorState

  private val firePathAbsWeight   = 0.32   // From paper
  private val firePathRelWeight   = 0.50   // From paper
  private val seleniumIDEWeight   = 0.81   // From paper
  private val montotoWeight       = 0.64   // From paper
  private val robulaplusWeight    = 0.87   // From paper

  override def init(element: MyElement, elements: Seq[MyElement]): MultiLocatorState =

    val document = getJDOMDocumentFromURL(element.url.getOrElse(""))
    val site = getDateAndSiteFromArchiveUrl(element.url.getOrElse(""))
//    println(s"Site: $site")
    val jdomElements: Seq[Element] = getJDOMElementsByXPath(element.xpath.idxpath, document)
    val firstElement: Element = jdomElements.head
    val absXPath = UtilsROBULAPlusNew.getAbsolutePathFromJDOMElement(firstElement)

    val firePathAbs = GeneratedLocator(
      xpath = absXPath,
      strategy = "ABSOLUTE_XPATH",
      weight = firePathAbsWeight
    )
//    println(s"ABSOLUTE_XPATH generated XPath: $absXPath")

    val firePathRel = GeneratedLocator(
      xpath = element.xpath.idxpath,
      strategy = "RELATIVE_XPATH",
      weight = firePathRelWeight
    )
//    println(s"RELATIVE_XPATH generated XPath: ${element.xpath.idxpath}")

    val startTime = System.nanoTime()
    val seleniumIDE = GeneratedLocator(
      xpath = XPathGeneratorUtil.getSeleniumIDEXPath(firstElement, document),
      strategy = "SELENIUM_IDE",
      weight = seleniumIDEWeight
    )
//    println(s"SELENIUM_IDE generated XPath: ${seleniumIDE.xpath}")

    val montoto = GeneratedLocator(
      xpath = XPathGeneratorUtil.getMontotoXPath(firstElement, document),
      strategy = "MONTOTO",
      weight = montotoWeight
    )
//    println(s"MONTOTO generated XPath: ${montoto.xpath}")

    val robulaPlusCandidates = buildRobulaPlusXPath(document, element.xpath.idxpath, element.url.getOrElse(""))
    val firstXPath: String = robulaPlusCandidates.head
    val robulaplus = GeneratedLocator(
      xpath = firstXPath,
      strategy = "ROBULA+",
      weight = robulaplusWeight
    )
//    println(s"ROBULA+ generated XPath: $firstXPath")
//    println(s"ROBULA+: $robulaplus")
//    if(element.xpath.chrome == "/html/body/div[1]/header/nav/div/div[2]/section[1]/header/a"){
//      println(
//        s"""FirePathAbs: ${firePathAbs.xpath}
//           |FirePathRel: ${firePathRel.xpath}
//           |SeleniumIDE: ${seleniumIDE.xpath}
//           |Montoto: ${montoto.xpath}
//           |ROBULA+: ${robulaplus.xpath}""".stripMargin
//      )
//    }

    val defaultState = MultiLocatorState(
      element = element,
      firePathAbs = firePathAbs,
      firePathRel = firePathRel,
      seleniumIDE = seleniumIDE,
      montoto = montoto,
      robulaplus = robulaplus,
      startTime = startTime
    )

    if(withRepair){
      // Try healed locators
      RepairStore.loadPromoted(element) match {
        case Some(healed) =>
//          println(s"[DEBUG] Healed locators loaded: ${healed.map(_.strategy).mkString(", ")}")
          def pick(name: String, fallback: GeneratedLocator): GeneratedLocator =
            healed.find(_.strategy.equalsIgnoreCase(name)).getOrElse(fallback)

          defaultState.copy(
            firePathAbs = pick("ABSOLUTE_XPATH", defaultState.firePathAbs),
            firePathRel = pick("RELATIVE_XPATH", defaultState.firePathRel),
            seleniumIDE = pick("SELENIUM_IDE", defaultState.seleniumIDE),
            montoto     = pick("MONTOTO", defaultState.montoto),
            robulaplus  = pick("ROBULA+", defaultState.robulaplus)
          )
        case None =>
//          println("[DEBUG] No healed locators found or MinConfirmations not reached.")
          defaultState
      }
    }else{
      defaultState
    }

  override def locate(state: MultiLocatorState, elements: Seq[MyElement]): Seq[(Double, MyElement)] =
    val shouldDebug = state.element.xpath.chrome == "/html/body/div[1]/div/div/div[11]/div[3]/div/div/div/div/button"
    val pageUrl: Option[String] = elements.headOption.flatMap(_.url)
    val siteNameOpt = getDateAndSiteFromArchiveUrl(pageUrl.getOrElse("")).map(_._2)
    val siteDateOpt = getDateAndSiteFromArchiveUrl(pageUrl.getOrElse("")).map(_._1)
    val siteNameStr = siteNameOpt.getOrElse("")
    val siteDateStr = siteDateOpt.getOrElse("")
    val filePath = getFilePathToChromeDOM(pageUrl.getOrElse(""))
    val file = new File(filePath)
    val domDocument = Load.htmlToJDOM(file)

    // Your existing 5 locators (ABS, REL, IDE, Montoto, Robula+)
    val locators = List(
      state.firePathAbs,
      state.firePathRel,
      state.seleniumIDE,
      state.montoto,
      state.robulaplus
    )

    // Turn DOM-based locators into results
    val domLocatorResults: Seq[LocatorResult] =
      locators.flatMap(locator => applyLocator(locator, domDocument))

    // Combine all voters
    val locatorResults: Seq[LocatorResult] = domLocatorResults

    val grouped: Map[String, Seq[GeneratedLocator]] =
      locatorResults.groupBy(_.elementLocatedXPath).view.mapValues(_.map(_.locator)).toMap

    val scoredVotes: Seq[(Double, String)] = grouped.map { case (absPath, locators) =>
      val vote = 1 - locators.map(l => 1 - l.weight).product
      (vote, absPath)
    }.toSeq

    val scoredVotesSorted = scoredVotes.sortBy(-_._1)

    // Debug print for all locators voted for that element
    if (shouldDebug) println(s"[$siteDateStr/$siteNameStr] Locator votes:")
    for ((vote, absPath) <- scoredVotesSorted) {
      val locs = grouped.getOrElse(absPath, Seq.empty)
      val locDetails = locs.map(l => s"${l.strategy}(weight=${l.weight})").mkString(", ")
      if (shouldDebug) println(s"[$siteDateStr/$siteNameStr] XPath: $absPath, Vote: $vote%.4f, Locators: $locDetails")
    }
    if (shouldDebug) println(s"[$siteDateStr/$siteNameStr] Chosen bestXPathOpt: ${scoredVotesSorted.headOption.map(_._2)}")

    // Print which strategies voted for the top element
    val topXPathOpt = scoredVotesSorted.headOption.map(_._2)
    topXPathOpt.foreach { topXPath =>
      val voters = grouped.getOrElse(topXPath, Seq.empty).map(_.strategy)
      if (shouldDebug) println(s"[$siteDateStr/$siteNameStr] Strategies voting for top XPath ($topXPath): ${voters.mkString(", ")}")
    }

    val bestXPathOpt: Option[String] = scoredVotesSorted.headOption.map(_._2)
    val endTime = System.nanoTime()
    val durationMs = (endTime - state.startTime) / 1_000_000.0
    logTiming(getDateAndSiteFromArchiveUrl(state.element.url.getOrElse("")).map(_._2), "Multi-Locator", state.element.xpath.chrome, durationMs)

    // VISTA fallback
     val bestOrVista: Option[String] =
       if withVista then
         bestXPathOpt.orElse {
           val vistaOpt = vistaAbsXPath(state, elements, domDocument)
           if (vistaOpt.isDefined) {
             if (shouldDebug) println(s"[$siteDateStr/$siteNameStr] [DEBUG] VISTA fallback triggered, produced XPath: ${vistaOpt.get}")
           }
           vistaOpt
         }
       else
         bestXPathOpt

//    println(s"[DEBUG] Final XPath: ${bestOrVista} | voters: ${locatorResults.map(_.locator.strategy).mkString(", ")} | fallbackUsed=${bestXPathOpt.isEmpty}")

    val result: Seq[(Double, MyElement)] = pageUrl match {
      case Some(url) =>
        bestOrVista match {
          case Some(bestXPath) =>
            val foundElements = getJDOMElementsByXPath(bestXPath, domDocument)

            if (foundElements.nonEmpty) {

              // ✅ REPAIR block, only if flag is true
              if (withRepair && scoredVotesSorted.nonEmpty) {
                val topScore    = scoredVotesSorted.head._1
                val secondScore = scoredVotesSorted.lift(1).map(_._1).getOrElse(0.0)
                if (shouldDebug) println(s"[$siteDateStr/$siteNameStr] [DEBUG] Repair block: topScore = $topScore, secondScore = $secondScore")

                val winnerJdom = foundElements.head
                val repairedLocators = Seq(
                  GeneratedLocator(getAbsolutePath(winnerJdom), "ABSOLUTE_XPATH", firePathAbsWeight),
                  GeneratedLocator(state.element.xpath.idxpath, "RELATIVE_XPATH", firePathRelWeight),
                  GeneratedLocator(XPathGeneratorUtil.getSeleniumIDEXPath(winnerJdom, domDocument), "SELENIUM_IDE", seleniumIDEWeight),
                  GeneratedLocator(XPathGeneratorUtil.getMontotoXPath(winnerJdom, domDocument), "MONTOTO", montotoWeight),
                  GeneratedLocator(buildRobulaPlusXPath(domDocument, getAbsolutePath(winnerJdom), pageUrl.getOrElse("")).head, "ROBULA+", robulaplusWeight)
                )
                if (shouldDebug) {
                  println(s"[$siteDateStr/$siteNameStr] [DEBUG] Candidate locators before repair:")
                  repairedLocators.foreach(l => println(s"[$siteDateStr/$siteNameStr]   Strategy: ${l.strategy}, XPath: ${l.xpath}"))
                  println(s"[$siteDateStr/$siteNameStr] [DEBUG] Repaired locators updated in RepairStore.")
                  println()
                  println()
                  println()
                }
                RepairStore.updateConfirm(state.element, repairedLocators)
              }
              // ✅ END repair block

              val scored = elements.map { el =>
                val maybeCandidate = getJDOMElementsByXPath(el.xpath.idxpath, domDocument).headOption
                val isMatch = maybeCandidate.exists { candidate =>
                  foundElements.exists(found => candidate eq found)
                }
                if (isMatch) (1.0, el) else (0.0, el)
              }

              val allZero = scored.forall { case (score, _) => score == 0.0 }
              if (allZero) {
                //              println(s"[DEBUG] No match found for best-voted XPath: $bestXPath")
              }
              scored.sortBy(-_._1)

            } else {
              //            println(s"[DEBUG] No element found in DOM for XPath: $bestXPath")
              elements.map(el => (0.0, el))
            }

          case None =>
            //          println("[DEBUG] No locator voted successfully.")
            elements.map(el => (0.0, el))
        }

      case None =>
        //      println("[DEBUG] No page URL found in elements.")
        elements.map(el => (0.0, el))
    }

    result

  def applyLocator(locator: GeneratedLocator, dom: Document): Option[LocatorResult] =
    val expr = XPathFactory.instance().compile(locator.xpath, Filters.element())
    val result = expr.evaluate(dom).asScala

    if result.size == 1 then
      val el = result.head
      val absPath = getAbsolutePath(el).toString
      Some(LocatorResult(locator, absPath))
    else
      None

  def getAbsolutePath(elem: Element): String =
    def path(e: Element): String =
      val parent = e.getParentElement
      val index = if parent != null then
        parent.getChildren(e.getName).indexOf(e) + 1
      else 1

      val selfPath = s"/${e.getName}[$index]"
      if parent != null then
        path(parent) + selfPath
      else
        selfPath

    path(elem)

  /** VISTA fallback: try to compute an absolute XPath via visual match when no voter succeeds. */
  private def vistaAbsXPath(
    state: MultiLocatorState,
    elements: Seq[MyElement],
    dom: Document
  ): Option[String] = {
    val aUrlOpt   = state.element.url
    val aShotPath = aUrlOpt.map(getFilePathToScreenshot).getOrElse("")
    val template  = aUrlOpt.map(u => getFilePathToTemplates(u, state.element)).getOrElse("")
    val bUrl      = elements.headOption.flatMap(_.url).getOrElse("")
    val bShotPath = getFilePathToScreenshot(bUrl)

    if (template.isEmpty) return None

    getMatchPointFromCache(bShotPath, template).flatMap { case (mx, my) =>
      elements
        .find(el => isPointInsideElement(mx, my, state.element.width, state.element.height, state.element.tag, el))
        .flatMap { elB =>
          getJDOMElementsByXPath(elB.xpath.idxpath, dom).headOption.map(getAbsolutePath)
        }
    }
  }

  def isPointInsideElement(x: Int, y: Int, w1: Int, h1: Int, targetTag: String, el: MyElement): Boolean = {
    val (ex, ey) = el.location
    val (w2, h2) = el.dimension

    // Return false if any of the coordinates or dimensions are zero
    if ex < 0 || ey < 0 || (ex == 0 && ey == 0) then return false
    val tolerance = 5

    val pointInside =
      x >= ex - tolerance && x <= ex + w2 + tolerance &&
        y >= ey - tolerance && y <= ey + h2 + tolerance

    val sameTag = el.tag.equalsIgnoreCase(targetTag)

    pointInside && sameTag
  }

  override def normalizer: Double = 1.0

  def logTiming(site: Option[String], algorithm: String, xpath: String, timeMs: Double): Unit =
    val dir = java.nio.file.Paths.get("results", "runtime", algorithm)
    java.nio.file.Files.createDirectories(dir)
    val logFile = dir.resolve("timing_log_1.csv").toFile
    val writer = new java.io.BufferedWriter(new java.io.FileWriter(logFile, true))
    writer.write(s"$site,$algorithm,$xpath,$timeMs\n")
    writer.close()
