package multilocator

import elements.Element
import similo.State

case class MultiLocatorState(element: Element,
                             firePathAbs: GeneratedLocator,
                             firePathRel: GeneratedLocator,
                             seleniumIDE: GeneratedLocator,
                             montoto: GeneratedLocator,
                             robulaplus: GeneratedLocator,
                             startTime: Long = 0L
                             ) extends State
