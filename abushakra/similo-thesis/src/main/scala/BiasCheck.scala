import java.nio.file.Path
import java.time.LocalDate
import scala.io.Source
import scala.util.Using

/** Ground-truth provenance check: for transitions labelled "deleted" although the new version contains a strong twin
  * of the element (same id, or same tag + text + href; see results/bias_deleted_twins.csv), which element do the
  * Similo variants pick? If they pick something else than the twin, the label coincides with a Similo failure. */
object BiasCheck:
  def main(args: Array[String]): Unit =
    val rows = Using.resource(Source.fromFile(args.headOption.getOrElse("results/bias_deleted_twins.csv")))(_.getLines().drop(1).toList)
    val locators = Seq("LegacySimilo" -> similo.legacy.LegacySimilo, "OptimizedSimilo" -> similo.optimized.OptimizedSimilo)
    println("locator;site;date;match;picked_twin;twin_rank;top_score;twin_score")
    val counts = scala.collection.mutable.Map[String, Int]().withDefaultValue(0)
    for row <- rows do
      val Array(site, prevDate, date, prevXPath, twinXPath, how) = row.split(";")
      val byDate = util.Load.getAllElementsByDateFor(Path.of("sites", site)).toMap
      val oldElements = byDate(LocalDate.parse(prevDate))
      val newElements = byDate(LocalDate.parse(date))
      val old = oldElements.find(_.xpath.chrome == prevXPath).get
      for (name, locator) <- locators do
        val state = locator.init(old, oldElements)
        val ranked = locator.locateSortedNormalized(state.asInstanceOf[locator.S], newElements)
        val rank = ranked.indexWhere(_._2.xpath.chrome == twinXPath)
        val twinScore = if rank >= 0 then ranked(rank)._1 else Double.NaN
        counts(s"$name total") += 1
        if rank == 0 then counts(s"$name picks twin") += 1
        println(f"$name;$site;$date;$how;${rank == 0};${rank + 1};${ranked.head._1}%.3f;$twinScore%.3f")
    counts.toSeq.sorted.foreach((k, v) => println(s"# $k: $v"))
