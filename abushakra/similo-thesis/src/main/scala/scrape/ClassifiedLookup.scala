package scrape

import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters.*
import scala.util.Try

object ClassifiedLookup {

  sealed trait Change { def asString: String }
  object Change {
    case object SAME        extends Change { val asString = "SAME" }
    case object MINOR       extends Change { val asString = "MINOR" }
    case object MAJOR       extends Change { val asString = "MAJOR" }
    case object DELETED     extends Change { val asString = "DELETED" }
    case object UNDEFINED   extends Change { val asString = "UNDEFINED" }

    def from(s: String): Change = s.trim.toUpperCase match {
      case "SAME"     => SAME
      case "MINOR"    => MINOR
      case "MAJOR"    => MAJOR
      case "DELETED"  => DELETED
      case _          => UNDEFINED
    }
  }

  // (site, date, elementKey) -> (elementChange, locatorChange)
  type Key  = (String, String, String)
  case class Row(elem: Change, loc: Change)

  def load(path: String): Map[Key, Row] = {
    if (!Files.exists(Paths.get(path))) return Map.empty
    val lines = Files.readAllLines(Paths.get(path)).asScala.toVector
    if (lines.isEmpty) return Map.empty

    val header = lines.head.split("[,;]").map(_.trim.toLowerCase)
    def idx(name: String) = header.indexOf(name)

    val iSite   = idx("site")
    val iDate   = idx("date")
    val iKey    = idx("element_key")
    val iElem   = idx("element_change")
    val iLoc    = idx("locator_change")

    lines.drop(1).flatMap { raw =>
      val cols = raw.split("[,;]", -1).map(_.trim)
      Try {
        val site = cols(iSite)
        val date = cols(iDate)
        val key  = cols(iKey)
        val ec   = Change.from(cols(iElem))
        val lc   = Change.from(cols(iLoc))
        ((site, date, key) -> Row(ec, lc))
      }.toOption
    }.toMap
  }
}