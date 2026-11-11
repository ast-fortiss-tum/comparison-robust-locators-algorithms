package similo.similo

import elements.Element
import similo.State

case class SimiloState(element: Element, startTime: Option[Long] = None) extends State
