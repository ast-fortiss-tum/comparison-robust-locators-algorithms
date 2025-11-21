package multilocator

/**
 * Used internally during voting to pair a locator with the element it finds (if it finds one uniquly)
 * @param locator The locator used
 * @param element Found element when applied on the updated DOM
 */
case class LocatorResult(
                        locator: GeneratedLocator,
                        elementLocatedXPath: String
                        )
