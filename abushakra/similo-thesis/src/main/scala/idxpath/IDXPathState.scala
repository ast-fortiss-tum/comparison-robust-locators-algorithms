package idxpath

import elements.Element
import similo.State

case class IDXPathState(element: Element, xpath: String) extends State
