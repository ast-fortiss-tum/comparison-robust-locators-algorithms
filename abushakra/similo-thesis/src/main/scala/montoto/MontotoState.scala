package montoto

import elements.Element
import similo.State

case class MontotoState(element: Element, xpath: String, startTime: Option[Long] = None) extends State
