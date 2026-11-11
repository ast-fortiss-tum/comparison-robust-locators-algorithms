package util

import ch.qos.logback.classic.Logger
import com.typesafe.config.{Config, ConfigFactory}
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.firefox.FirefoxDriver
import org.slf4j.LoggerFactory
import scrape.SetupDrivers
object Setup:

  var locked = false
  var locked2 = false
  var count = 0
  var currentWebsiteFromVersionA: String = ""
  var currentWebsiteFromVersionB: String = ""

  //driver for performing the robula+ on a given element
  lazy val firefox: ChromeDriver = SetupDrivers.initChromeDriver()

  //driver for finding the element by xpath on elements from version B
  lazy val firefox2: ChromeDriver = SetupDrivers.initChromeDriver()

  lazy val chrome: ChromeDriver = SetupDrivers.initChromeDriver()

  lazy val logger: Logger =
    val log = LoggerFactory.getLogger("").asInstanceOf[Logger]
    // log.setLevel(config.getInt("loglevel"))
    log

  lazy val config: Config = ConfigFactory.load()
