package vista

import org.apache.commons.text.similarity.LevenshteinDistance

import elements.Element
import similo.{Locator, State}
import similo.comparing.StringCompare
import util.Load
import util.Setup.count
import Load.getFilePathToScreenshot
import Load.getFilePathToTemplates
import Load.getDateAndSiteFromArchiveUrl
import Load.cropTemplate
import Load.matchTemplate
import Load.getMatchPointFromCache
import java.nio.file.{Files, Paths}

case class Vista() extends Locator:

  override type S = VistaState

  override def init(element: Element, elements: Seq[Element]): VistaState = VistaState(element)

  override def locate(state: VistaState, elements: Seq[Element]): Seq[(Double, Element)] =

    val templatePath = getFilePathToTemplates(state.element.url.getOrElse(""), state.element)
    val pageUrlB = elements.headOption.flatMap(_.url)
    val filePathB = getFilePathToScreenshot(pageUrlB.getOrElse(""))

    val startTime = System.nanoTime()
    // If template png is missing, (re)crop it from version A's screenshot
    val needCrop = templatePath.isEmpty || !Files.exists(Paths.get(templatePath))
    if needCrop then
      val filePathA = getFilePathToScreenshot(state.element.url.getOrElse(""))
      val cropped = cropTemplate(
        filePathA,
        templatePath,
        state.element.x,
        state.element.y,
        state.element.width,
        state.element.height
      )
      if !cropped then
        // Cropping failed → return zeros
//        count = count + 1
//        println(count)
        return elements.map(el => (0.0, el))

    val matchOpt = getMatchPointFromCache(filePathB, templatePath)
    val endTime = System.nanoTime()
    val durationMs = (endTime - startTime) / 1_000_000.0
    logTiming(getDateAndSiteFromArchiveUrl(state.element.url.getOrElse("")).map(_._2), "Vista", state.element.xpath.chrome, durationMs)
    // Gate debug logs to accuweather in 2018 only
//    val debugGate: Boolean = {
//      val maybe = getDateAndSiteFromArchiveUrl(state.element.url.getOrElse(""))
//      maybe.exists { case (date, site) => site == "accuweather" && date.startsWith("2018") }
//    }
//    if (debugGate) {
//      println(s"[Vista DEBUG] ${state.element.xpath.chrome}, matchPoint=$matchOpt")
//    }
    // We have a template on disk at this point → run visual match
    val result: Seq[(Double, Element)] =
      matchOpt match
        case Some((matchX, matchY)) =>
          elements.map { el =>
            val score = isPointInsideElement(matchX, matchY, state.element.width, state.element.height, state.element.tag, state.element.getVisibleText, state.element.id, el)
            (score, el)
          }
        case None =>
          elements.map(el => (0.0, el))

    result.sortBy(-_._1)

  def isPointInsideElement(x: Int, y: Int, w1: Int, h1: Int, targetTag: String, targetText: Option[String], targetID: Option[String], el: Element): Double = {
    val (ex, ey) = el.location
    val (w2, h2) = el.dimension

    // Return 0 if any of the coordinates or dimensions are negative or both are zero
    if ex < 0 || ey < 0 || (ex == 0 && ey == 0) then return 0.0

    val tolerance = 5

    val pointInside =
      x >= ex - tolerance && x <= ex + w2 + tolerance &&
        y >= ey - tolerance && y <= ey + h2 + tolerance

    val sameTag = el.tag.equalsIgnoreCase(targetTag)

    if pointInside && sameTag then
      val tgtRaw = targetText.getOrElse("")
      val elRaw  = el.getVisibleText.getOrElse("")

      def norm(s: String): String = s.trim.replaceAll("\\s+", " ").toLowerCase

      val tgtN = norm(tgtRaw)
      val elN  = norm(elRaw)
      val dist = new LevenshteinDistance(50).apply(tgtN, elN)
      val maxLen = math.min(math.max(tgtN.length, elN.length), 50)
      val simRaw =
        if maxLen == 0 then 1.0
        else if dist < 0 then 0.0
        else 1.0 - dist.toDouble / maxLen
      val sim = math.max(0.0, math.min(1.0, simRaw))

      // Debug raw and normalized texts + similarity
//      println(s"[Vista][TextSim] target='${tgtRaw}' | element='${elRaw}' | norm_target='${tgtN}' | norm_element='${elN}' | sim=${sim}")

      sim // 0..1
    else 0.0
  }

  override def normalizer: Double = 1.0

  def logTiming(site: Option[String], algorithm: String, xpath: String, timeMs: Double): Unit =
    val dir = java.nio.file.Paths.get("results", "runtime", algorithm)
    java.nio.file.Files.createDirectories(dir)
    val logFile = dir.resolve("timing_log_5.csv").toFile
    val writer = new java.io.BufferedWriter(new java.io.FileWriter(logFile, true))
    writer.write(s"$site,$algorithm,$xpath,$timeMs\n")
    writer.close()



//  override def locate(state: VistaState, elements: Seq[Element]): Seq[(Double, Element)] =
//  val templateOut = getFilePathToTemplates(state.element.url.getOrElse(""), state.element)
//  val filePathA = getFilePathToScreenshot(state.element.url.getOrElse(""))
//  val pageUrl: Option[String] = elements.headOption.flatMap(_.url)
//  val filePathB = getFilePathToScreenshot(pageUrl.getOrElse(""))
//
//  val cropSuccess = cropTemplate(
//    filePathA, templateOut,
//    state.element.x, state.element.y,
//    state.element.width, state.element.height
//  )
//
//  val result: Seq[(Double, Element)] =
//    if cropSuccess then
//      matchTemplate(filePathB, templateOut) match {
//      case Some((matchX, matchY)) =>
//        println(s"[Vista] Best match at ($matchX, $matchY)")
//        elements.map { el =>
//          val score = if isPointInsideElement(matchX, matchY, el) then 1.0 else 0.0 //(matchX==el.x && matchY==el.y)
//          (score, el)
//        }
//
//      case None =>
//        println("[Vista] No match found in template matching")
//        elements.map(el => (0.0, el))
//    }
//    else
//      println("[Vista] Cropping failed")
//  elements.map(el => (0.0, el))
//
//  result.sortBy(-_._1)