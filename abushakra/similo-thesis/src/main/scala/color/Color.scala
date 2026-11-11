package color

import elements.Element
import similo.{Locator, State}
import util.Load.getFilePathToTemplates
import similo.AttributeComparison
import similo.comparing._

case class Color() extends Locator:

  override type S = ColorState

  override def init(element: Element, elements: Seq[Element]): ColorState = ColorState(element)

  override def locate(state: ColorState, elements: Seq[Element]): Seq[(Double, Element)] = ???

  override def normalizer: Double = ???

