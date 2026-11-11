package absxpath

import elements.Element
import similo.State

case class absXPathState(element: Element, xpath: String) extends State
