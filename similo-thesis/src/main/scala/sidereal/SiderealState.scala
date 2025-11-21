package sidereal

import elements.Element
import similo.State

case class SiderealState(element: Element, xpath: String) extends State
