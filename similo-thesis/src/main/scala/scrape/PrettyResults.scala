package scrape

import evalutation.EvaluationStatistics
import evalutation.EvaluationStatistics.{ElementLocalisation, Result, MatchType}
import elements.Element

object PrettyResults {

  /** Print one algorithm’s EvaluationStatistics in a hierarchical, readable way. */
  def print(stats: EvaluationStatistics, maxFound: Int = 3): Unit = {
    val bySite: Seq[(String, Seq[Result])] =
      stats.results.groupBy(_.site).toSeq.map { case (site, els) => site -> els.flatMap(_.results).sortBy(_.date) }
        .sortBy(_._1)

    bySite.foreach { case (site, results) =>
      println(s"=== SITE: $site ===")
      // group by date so it’s easy to scan timelines
      results.groupBy(_.date).toSeq.sortBy(_._1).foreach { case (date, rs) =>
        println(s"- Date: $date")
        rs.foreach { r =>
          println(formatResult(r, maxFound))
          println()
        }
      }
      println()
    }
  }

  /** Print multiple algorithms side-by-side (labels provided in the Seq). */
  def printAll(all: Seq[(String, EvaluationStatistics)], maxFound: Int = 5): Unit = {
    all.foreach { case (name, stats) =>
      println(s"######## Algorithm: $name ########")
      print(stats, maxFound)
    }
  }

  // ---------- format helpers ----------

  private def formatResult(r: Result, maxFound: Int): String = {
    val sb = new StringBuilder

    sb.append(s"  • Classification: ${r.classification}\n")
    sb.append(s"    ExistsOnNew?   ${r.existsOnNewVersion}\n")
    sb.append(s"    ExactMatch?    ${r.exactMatch}\n")
    sb.append(s"    Score/Possible ${f(r.score)} / ${f(r.possibleScore)}\n")

    sb.append("    ToFind (old):\n")
    sb.append(indent(      formatElement(r.toFind), 6)).append('\n')
    sb.append("    Target (new):\n")
    sb.append(indent(      formatElement(r.target), 6)).append('\n')

    val foundTop = r.found.take(maxFound)
    if (foundTop.isEmpty) sb.append("    Found: (none)\n")
    else {
      sb.append(s"    Found (top ${foundTop.size}):\n")
      foundTop.zipWithIndex.foreach { case ((el, mt, score), i) =>
        sb.append(s"      #${i + 1}  match=${mt}  sim=${f(score)}\n")
        sb.append(indent(formatElement(el), 8)).append('\n')
      }
    }

    sb.toString
  }

  private def formatElement(e: Element): String = {
    def cut(s: String, n: Int = 80) = if (s.length <= n) s else s.take(n - 1) + "…"

    val id    = e.id.getOrElse("-")
    val cls   = e.className.getOrElse("-")
    val name  = e.name.getOrElse("-")
    val text  = e.getVisibleText.getOrElse("-")
    val href  = e.href.getOrElse("-")
    val alt   = e.alt.getOrElse("-")
    val loc   = s"(${e.x}, ${e.y})"
    val dim   = s"(${e.width}×${e.height})"
    val xp    = cut(e.xpath.chrome, 120)

    // Select a few high-signal attributes to keep output compact
    s"""tag=${e.tag}
       |xpath=${xp}
       |id=$id  class=${cut(cls, 60)}  name=${name}
       |text=${cut(text, 80)}  href=${cut(href, 80)}  alt=${cut(alt, 80)}
       |loc=${loc}  dim=${dim}""".stripMargin
  }

  private def indent(s: String, spaces: Int): String = {
    val pad = " " * spaces
    s.linesIterator.map(pad + _).mkString("\n")
  }

  private def f(d: Double): String = f"$d%.3f"
}