package multilocator

import elements.Element

/** Evaluation-only side channel: the evaluation sets the true target of the lookup it is about to run, so that the repair
  * counters can classify each locator against the ground truth (Table III of the Multi-Locator paper). Locators must never
  * use it to decide anything; it is read only to count. */
object GroundTruthProbe:
  val current = new ThreadLocal[Option[Element]]:
    override def initialValue(): Option[Element] = None
