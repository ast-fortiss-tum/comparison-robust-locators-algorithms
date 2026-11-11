import org.jdom2.Element
import scala.jdk.CollectionConverters._

object MontotoXPathExtractor {

  private val blacklist = Set(
    "style", "href", "src", "onclick", "onload",
    "xmlns", "xmlns:xlink", "tile?apikey", "data-galink",
    "srcset", "data-src", "data-srcset", "data-original",
    "data-analytics", "data-tracking", "data-testid", "data-widget",
    "integrity", "crossorigin", "referrerpolicy", "ping"
  )

  private def quote(value: String): String = {
    if (value.contains("\"")) s"'$value'" else s""""$value""""
  }

  def getMontotoXPath(element: Element): String = {
    val tag = element.getName

    val predicates = element.getAttributes.asScala.flatMap { attr =>
      val name = attr.getName
      val value = attr.getValue.trim
      if (!blacklist.contains(name) && value.nonEmpty)
        Some(s"@$name=${quote(value)}")
      else None
    }

    if (predicates.nonEmpty)
      s"//$tag[${predicates.mkString(" and ")}]"
    else
      s"//$tag"
  }
}