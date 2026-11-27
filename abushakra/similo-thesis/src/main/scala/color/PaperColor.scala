package color

import elements.Element
import similo.{Locator, State}
import java.nio.file.{Files, Paths}
import java.security.MessageDigest

/** COLOR as specified in Kirinuki, Tanno, Natsukawa, "COLOR: Correct Locator Recommender for Broken Test Scripts using Various Clues in Web Application"
  * (SANER 2019; chapter 2 of Kirinuki's dissertation, Osaka University). Earlier versions of this benchmark ran the same attributes inside Similo's scoring; this version follows the paper:
  *  - Aggregation (Eq. 2.4): S(e,e') = sum_p s_p*w_p / sum_p w_p over the properties P that BOTH elements have. A property missing on either side is ignored,
  *    not scored 0 as in Similo. Scores are therefore in [0,1] and an element with few attributes is not penalised for them.
  *  - Image clue: equality (1/0) of the element image (the paper compares Base64 encodings, here the bytes of the cropped template), not a perceptual hash.
  *  - Link text only for <a> elements; label = text linked to an input form (the nearest-text approximation: the neighbour text of input/select/textarea elements).
  *  - Weights: the paper's automatic weights (stability x uniqueness, Table 2.7), x-axis 0.60 (LegacyColor used 0.72).
  * Similarities: numeric 1 - |a-b|/Max(p) (Eq. 2.1, Max = screen size), equality for image/tag/size (Eq. 2.2), 1 - Levenshtein/MaxLength otherwise (Eq. 2.3).
  * Candidates are all visible elements of the new page (the paper only considers input, button, a, img and select, because its targets are operable elements). */
object PaperColor extends Locator:

  case class ColorState(element: Element) extends State
  override type S = ColorState

  private val maxX = 1920.0; private val maxY = 1080.0

  private def lev(a: String, b: String): Int =
    if a == b then 0
    else if a.isEmpty then b.length
    else if b.isEmpty then a.length
    else
      var prev = Array.tabulate(b.length + 1)(identity)
      for i <- 1 to a.length do
        val cur = new Array[Int](b.length + 1); cur(0) = i
        for j <- 1 to b.length do
          cur(j) = math.min(math.min(cur(j - 1) + 1, prev(j) + 1), prev(j - 1) + (if a(i - 1) == b(j - 1) then 0 else 1))
        prev = cur
      prev(b.length)
  private def strSim(a: String, b: String): Double = 1.0 - lev(a, b).toDouble / math.max(a.length, b.length)
  private def numSim(a: Int, b: Int, max: Double): Double = math.max(0.0, math.min(1.0, 1.0 - math.abs(a - b).toDouble / max))

  private def imageDigest(e: Element): Option[String] =
    val path = util.Load.getFilePathToTemplates(e.url.getOrElse(""), e)
    if path.nonEmpty && Files.exists(Paths.get(path)) then
      Some(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Paths.get(path))).map("%02x".format(_)).mkString)
    else None

  private def nonEmpty(s: Option[String]): Option[String] = s.map(_.trim).filter(_.nonEmpty)
  private def linkText(e: Element): Option[String] = if e.tag.equalsIgnoreCase("a") then nonEmpty(e.getVisibleText) else None
  private val inputTags = Set("INPUT", "SELECT", "TEXTAREA")
  private def label(e: Element): Option[String] = if inputTags.contains(e.tag.toUpperCase) then nonEmpty(e.neighbourText) else None
  private def attr(e: Element, k: String): Option[String] = e.attributes.get(k).filter(_.nonEmpty)

  /** (weight, similarity if both elements have the property) for each of the 19 properties */
  private def terms(a: Element, b: Element, imgA: Option[String]): Seq[(Double, Option[Double])] =
    def str(w: Double, x: Option[String], y: Option[String]) = (w, for p <- x; q <- y yield strSim(p, q))
    def eq(w: Double, x: Option[String], y: Option[String]) = (w, for p <- x; q <- y yield if p == q then 1.0 else 0.0)
    Seq(
      str(0.86, attr(a, "id"), attr(b, "id")),
      str(0.35, attr(a, "class"), attr(b, "class")),
      str(0.80, attr(a, "name"), attr(b, "name")),
      str(0.61, attr(a, "value"), attr(b, "value")),
      str(0.44, attr(a, "type"), attr(b, "type")),
      (0.21, Some(if a.tag.equalsIgnoreCase(b.tag) then 1.0 else 0.0)),
      str(0.57, attr(a, "alt"), attr(b, "alt")),
      str(0.62, attr(a, "src"), attr(b, "src")),
      str(0.62, attr(a, "href"), attr(b, "href")),
      eq(0.26, attr(a, "size"), attr(b, "size")),
      str(0.66, attr(a, "onclick"), attr(b, "onclick")),
      (0.33, Some(numSim(a.height, b.height, maxY))),
      (0.66, Some(numSim(a.width, b.width, maxX))),
      (0.72, Some(strSim(a.xpath.chrome, b.xpath.chrome))),
      (0.60, Some(numSim(a.x, b.x, maxX))),
      (0.65, Some(numSim(a.y, b.y, maxY))),
      str(0.76, linkText(a), linkText(b)),
      str(0.31, label(a), label(b)),
      eq(0.12, imgA, imageDigest(b))
    )

  override def locate(state: ColorState, elements: Seq[Element]): Seq[(Double, Element)] =
    val imgA = imageDigest(state.element)
    elements.map { e =>
      val t = terms(state.element, e, imgA).collect { case (w, Some(s)) => (w, s) }
      val wsum = t.map(_._1).sum
      (if wsum > 0 then t.map((w, s) => w * s).sum / wsum else 0.0, e)
    }.sortBy(_._1).reverse

  override def init(element: Element, elements: Seq[Element]): ColorState = ColorState(element)
  override def normalizer: Double = 1.0
