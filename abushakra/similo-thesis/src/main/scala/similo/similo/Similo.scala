package similo.similo

import elements.Element
import similo.{AttributeComparison, Locator}
import util.Load.getDateAndSiteFromArchiveUrl

case class Similo(comparisons: AttributeComparison[?]*) extends Locator:

  override type S = SimiloState

  def score(left: Element, right: Element): Double =
    score(SimiloState(left), right)

  def locateSorted(element: Element, elements: Seq[Element]): Seq[(Double, Element)] =
    locate(element, elements).sortBy(_._1).reverse

  def locate(element: Element, elements: Seq[Element]): Seq[(Double, Element)] =
    locate(SimiloState(element), elements)

  override def locate(state: SimiloState, elements: Seq[Element]): Seq[(Double, Element)] =
    val startTime = System.nanoTime()
    val results = elements.map(e => (score(state, e), e)).sortBy(_._1).reverse
    val endTime = System.nanoTime()
    val durationMs = (endTime - startTime) / 1_000_000.0
    logTiming(getDateAndSiteFromArchiveUrl(state.element.url.getOrElse("")).map(_._2), "OptimizedSimilo", state.element.xpath.chrome, durationMs)
    results

  def normalizedScore(left: Element, right: Element): Double =
    normalizedScore(SimiloState(left), right)

  def normalizedScore(state: SimiloState, element: Element): Double =
    score(state, element) / normalizer

  private def score(state: SimiloState, element: Element): Double =
    comparisons.map(_.score(state.element, element)).sum

  def normalizedLocate(element: Element, elements: Seq[Element]): Seq[(Double, Element)] =
    normalizedLocate(SimiloState(element), elements)

  def normalizedLocate(state: SimiloState, elements: Seq[Element]): Seq[(Double, Element)] =
    locateSorted(state, elements).map((score, element) => (score / normalizer, element))

  override def normalizer: Double = comparisons.map(_.weight).sum

  override def init(element: Element, elements: Seq[Element]): SimiloState = SimiloState(element)

  def logTiming(site: Option[String], algorithm: String, xpath: String, timeMs: Double): Unit =
    val dir = java.nio.file.Paths.get("results", "runtime", algorithm)
    java.nio.file.Files.createDirectories(dir)
    val logFile = dir.resolve("timing_log_1.csv").toFile
    val writer = new java.io.BufferedWriter(new java.io.FileWriter(logFile, true))
    writer.write(s"$site,$algorithm,$xpath,$timeMs\n")
    writer.close()
