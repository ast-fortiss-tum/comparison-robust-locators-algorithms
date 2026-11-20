package selenium_ide

import elements.Element
import similo.State

case class SeleniumIDEState(element: Element, xpath: String, startTime: Option[Long] = None) extends State
