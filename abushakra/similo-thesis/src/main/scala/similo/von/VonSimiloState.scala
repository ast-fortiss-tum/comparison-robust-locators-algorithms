package similo.von

import elements.Element
import similo.State

case class VonSimiloState(overlap: Seq[Element], startTime: Long = 0L) extends State
