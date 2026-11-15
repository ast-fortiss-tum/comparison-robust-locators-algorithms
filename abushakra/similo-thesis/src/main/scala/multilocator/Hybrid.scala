package multilocator

import elements.Element as MyElement
import similo.{Locator, State}
import similo.optimized.OptimizedSimilo
import org.jdom2.Document

import scala.collection.concurrent.TrieMap
import scala.jdk.CollectionConverters.*

/** Hybrids of the XPath-voting MultiLocator (no VISTA) and the OptimizedSimilo relocator.
  *
  *  - Fallback: the voted XPath answer; when no locator resolves to a candidate, Similo's top candidate.
  *  - Voter:    Similo's top candidate is a sixth voter of weight `similoWeight`; the best-voted element wins.
  *  - Gated:    the voted answer if it is among Similo's top `k` candidates, otherwise Similo's top candidate.
  *  - Repair:   Fallback, and the five locators are regenerated from the element that was found, so the next version is
  *              located with them (in-memory, so runs do not influence each other).
  */
enum HybridMode:
  case Fallback, Voter, Gated, Repair

case class HybridState(element: MyElement, ml: MultiLocatorState, sim: Any) extends State

final case class Analysis(votes: Map[String, Double], genAbs: Option[String], genVote: Double, genElems: Seq[MyElement],
                          simTop: Seq[(MyElement, Option[String])], dom: Option[Document])

case class Hybrid(mode: HybridMode, k: Int = 3, similoWeight: Double = 0.9) extends Locator:

  override type S = HybridState

  private val ml = MultiLocator(false, false)
  private val sim = OptimizedSimilo

  // Everything that does not depend on the repair state is computed once and shared by the hybrids run in one JVM.
  private val baseStates = Hybrid.baseStates
  private val simCache = Hybrid.simCache
  private val analyses = Hybrid.analyses
  private val healed = Hybrid.healed(mode)

  private def key(e: MyElement) = (e.url.getOrElse(""), e.xpath.chrome)

  override def init(element: MyElement, elements: Seq[MyElement]): HybridState =
    val base = baseStates.getOrElseUpdate(key(element), ml.init(element, elements))
    val st = if mode == HybridMode.Repair then healed.get(key(element)).map(ml.withHealed(base, _)).getOrElse(base) else base
    HybridState(element, st, sim.init(element, elements))

  private def similoTop(state: HybridState, elements: Seq[MyElement]): Seq[MyElement] =
    val bUrl = elements.headOption.flatMap(_.url).getOrElse("")
    simCache.getOrElseUpdate((key(state.element), bUrl),
      sim.locateSorted(state.sim.asInstanceOf[sim.S], elements).take(k).map(_._2))

  private def analyse(state: HybridState, elements: Seq[MyElement], simTop: Seq[MyElement], keepDom: Boolean): Analysis =
    val (votes, dom) = ml.voteTable(state.ml, elements)
    val genAbs = votes.headOption.map(_._2)
    val genElems = genAbs.map(a => ml.elementsAtAbsPath(a, elements, dom)).getOrElse(Seq.empty)
    Analysis(votes.map(v => v._2 -> v._1).toMap, genAbs, votes.headOption.map(_._1).getOrElse(0.0), genElems,
      simTop.map(e => (e, ml.absPathOf(e, dom))), if keepDom then Some(dom) else None)

  override def locate(state: HybridState, elements: Seq[MyElement]): Seq[(Double, MyElement)] =
    val bUrl = elements.headOption.flatMap(_.url).getOrElse("")
    val top = similoTop(state, elements)
    val a =
      if mode == HybridMode.Repair then analyse(state, elements, top, keepDom = true)
      else analyses.getOrElseUpdate((key(state.element), bUrl), analyse(state, elements, top, keepDom = false))

    val genValid = a.genAbs.isDefined && a.genElems.nonEmpty
    val simAnswer: Seq[MyElement] = top.headOption.toSeq
    val simAbs: Option[String] = a.simTop.headOption.flatMap(_._2)
    val (answer, winnerAbs): (Seq[MyElement], Option[String]) = mode match
      case HybridMode.Fallback | HybridMode.Repair =>
        if genValid then (a.genElems, a.genAbs) else (simAnswer, simAbs)
      case HybridMode.Gated =>
        if genValid && a.simTop.exists(_._2 == a.genAbs) then (a.genElems, a.genAbs) else (simAnswer, simAbs)
      case HybridMode.Voter =>
        if !genValid then (simAnswer, simAbs)
        else if simAbs == a.genAbs then (a.genElems, a.genAbs)
        else
          val simVote = 1 - (1 - simAbs.flatMap(a.votes.get).getOrElse(0.0)) * (1 - similoWeight)
          if simVote > a.genVote then (simAnswer, simAbs) else (a.genElems, a.genAbs)

    if mode == HybridMode.Repair then
      for abs <- winnerAbs; dom <- a.dom; w <- org.jdom2.xpath.XPathFactory.instance()
          .compile(abs, org.jdom2.filter.Filters.element()).evaluate(dom).asScala.headOption do
        scala.util.Try(ml.regenerate(state.ml, w, dom, bUrl)).foreach(l => healed.put(key(state.element), l))

    val chosen = answer.toSet
    elements.map(e => (if chosen.contains(e) then 1.0 else 0.0, e))

  override def normalizer: Double = 1.0

object Hybrid:
  val baseStates = TrieMap[(String, String), MultiLocatorState]()
  val simCache = TrieMap[((String, String), String), Seq[MyElement]]()
  val analyses = TrieMap[((String, String), String), Analysis]()
  private val repairStores = TrieMap[HybridMode, TrieMap[(String, String), Seq[GeneratedLocator]]]()
  def healed(mode: HybridMode): TrieMap[(String, String), Seq[GeneratedLocator]] =
    repairStores.getOrElseUpdate(mode, TrieMap())
