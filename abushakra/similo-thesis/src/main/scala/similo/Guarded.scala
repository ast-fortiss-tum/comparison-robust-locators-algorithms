package similo

import elements.Element

import scala.util.{Failure, Success, Try}

case class GuardedState(inner: Option[Any]) extends State

/**
 * Wraps a locator so that a failure to generate a locator for one element (e.g. the element cannot be resolved in a
 * regenerated DOM) does not abort the whole evaluation: that element gets no locator and every candidate scores 0.
 * The same rule is applied to all DOM-based locators so that they are compared on equal terms.
 */
case class Guarded(inner: Locator) extends Locator:

  override type S = GuardedState

  @volatile private var failures = 0

  def failedInits: Int = failures

  override def init(element: Element, elements: Seq[Element]): GuardedState =
    Try(inner.init(element, elements)) match
      case Success(s) => GuardedState(Some(s))
      case Failure(_) =>
        failures += 1
        GuardedState(None)

  override def locate(state: GuardedState, elements: Seq[Element]): Seq[(Double, Element)] =
    state.inner match
      case Some(s) => Try(inner.locate(s.asInstanceOf[inner.S], elements)).getOrElse(elements.map(e => (0.0, e)))
      case None    => elements.map(e => (0.0, e))

  override def normalizer: Double = inner.normalizer
