package multilocator

/**
 * Represents a single locator including how it was generated and how realiable it is.
 * @param xpath The XPath experession
 * @param strategy The algorithm used to generate it ("ABSOLUTE_XPATH", "RELATIVE_XPATH", "SELENIUM_IDE", "MONTOTO", "ROBULA+")
 * @param weight Normalized score on how realiable the locator based on pre-knowlodge
 */
case class GeneratedLocator(
                           xpath: String,
                           strategy: String,
                           weight: Double
                           )
