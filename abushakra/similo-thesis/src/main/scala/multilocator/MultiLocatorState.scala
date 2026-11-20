package multilocator

import elements.Element
import similo.State

case class MultiLocatorState(element: Element,
                             firePathAbs: GeneratedLocator,
                             firePathRel: GeneratedLocator,
                             seleniumIDE: GeneratedLocator,
                             montoto: GeneratedLocator,
                             robulaplus: GeneratedLocator,
                             startTime: Long = 0L,
                             healed: Set[String] = Set.empty,                   // strategies whose locator was replaced by a repaired one
                             originals: Map[String, GeneratedLocator] = Map.empty, // the locators before repair
                             inSet: Set[String] = Set("ABSOLUTE_XPATH", "RELATIVE_XPATH", "SELENIUM_IDE", "MONTOTO", "ROBULA+") // the paper's set L: locators that uniquely select the target on the original page
                             ) extends State
