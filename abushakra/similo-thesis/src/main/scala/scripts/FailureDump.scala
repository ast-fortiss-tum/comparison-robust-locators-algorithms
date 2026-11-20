package scripts

import elements.Element
import evalutation.EvaluationData.getSiteData
import similo.Locator

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, Paths}

/**
 * Dumps every element a repair algorithm does not rank first, with what is needed to group the misses by cause:
 * the rank and score of the true element, what was ranked first instead, whether that is an ancestor or descendant
 * of the truth (a granularity mismatch), and how many near-identical twins the truth has on the new page.
 *
 *   runMain scripts.FailureDump [site ...]       (default: every site with xpaths.csv and classified.csv)
 * Writes results/failures/<algorithm>.csv and prints a per-algorithm summary.
 */
object FailureDump:

  private val algorithms: Seq[(String, Locator)] = Seq(
    "OptimizedSimilo" -> similo.optimized.OptimizedSimilo,
    "LegacySimilo" -> similo.legacy.LegacySimilo,
    "VonSimilo" -> similo.legacy.LegacyVonSimilo,
    "COLOR" -> color.LegacyColor
  )

  def main(args: Array[String]): Unit =
    java.util.Locale.setDefault(java.util.Locale.US)
    val sites: Seq[Path] =
      if args.nonEmpty then args.toSeq.map(s => Paths.get("sites", s))
      else util.Load.getFullLoadedSuitableSites
    val only = sys.env.get("ALGOS").map(_.split(",").map(_.trim).toSet)
    val out = Paths.get("results", "failures")
    Files.createDirectories(out)
    for (name, locator) <- algorithms if only.forall(_.contains(name)) do
      val rows = Seq.newBuilder[String]
      rows += "site;date;element_change;locator_change;truth_rank;truth_score;top_score;relation;twins;old_tag;truth_tag;top_tag;old_text;truth_text;top_text;old_xpath;truth_xpath;top_xpath;dx;dy"
      var total, misses = 0
      for site <- sites do
        val data = getSiteData(site)
        for dd <- data.data; pair <- dd.location if !pair.oldElement.isEmpty && !pair.target.isEmpty do
          total += 1
          val state = locator.init(pair.oldElement, dd.oldElements)
          val ranked = locator.locateSortedNormalized(state, dd.newElements)
          val truthX = pair.target.xpath.chrome
          if ranked.headOption.forall(_._2.xpath.chrome != truthX) then
            misses += 1
            val idx = ranked.indexWhere(_._2.xpath.chrome == truthX)
            val (topScore, top) = ranked.headOption.getOrElse((0.0, Element.empty))
            val truthScore = if idx >= 0 then ranked(idx)._1 else Double.NaN
            val tx = top.xpath.chrome
            val relation =
              if tx.startsWith(truthX + "/") then "top-inside-truth"
              else if truthX.startsWith(tx + "/") then "top-contains-truth"
              else if parent(tx) == parent(truthX) then "sibling"
              else "elsewhere"
            val twins = dd.newElements.count(e => e.xpath.chrome != truthX && e.tag == pair.target.tag &&
              text(e) == text(pair.target) && text(e).nonEmpty)
            rows += Seq(site.getFileName, dd.date, pair.classification.element, pair.classification.locator,
              if idx >= 0 then idx + 1 else -1, f"$truthScore%.4f", f"$topScore%.4f", relation, twins,
              pair.oldElement.tag, pair.target.tag, top.tag, clean(text(pair.oldElement)), clean(text(pair.target)),
              clean(text(top)), pair.oldElement.xpath.chrome, truthX, tx,
              top.x - pair.target.x, top.y - pair.target.y).mkString(";")
      Files.writeString(out.resolve(s"$name.csv"), rows.result().mkString("\n") + "\n", UTF_8)
      println(f"$name: $misses misses of $total (${100.0 * (total - misses) / total}%.2f%% top-1)")

  private def text(e: Element): String = e.getVisibleText.getOrElse("").trim.replaceAll("\\s+", " ")
  private def clean(s: String): String = s.replace(";", ",").take(60)
  private def parent(x: String): String = x.substring(0, math.max(0, x.lastIndexOf('/')))
