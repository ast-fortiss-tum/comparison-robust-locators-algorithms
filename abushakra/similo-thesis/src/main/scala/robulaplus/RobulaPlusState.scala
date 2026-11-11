package robulaplus

import elements.Element
import similo.State

case class RobulaPlusState(element: Element, xpathRobulaPlus: String, startTime: Option[Long] = None) extends State
