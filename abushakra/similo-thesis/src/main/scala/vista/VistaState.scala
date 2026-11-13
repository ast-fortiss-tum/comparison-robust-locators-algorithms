package vista

import elements.Element
import similo.State

case class VistaState(element: Element, startTime: Option[Long] = None) extends State
