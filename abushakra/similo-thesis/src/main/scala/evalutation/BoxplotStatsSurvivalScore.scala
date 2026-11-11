package evalutation

import java.nio.file.{Files, Paths, Path, StandardOpenOption}
import scala.io.Source
import java.util.Locale

object BoxplotStatsSurvivalScore {

  // ---- Config ----
  private val Verbose = true
  private val ResultsDir   = Paths.get("results", "survivability_results")
  private val Algorithms = Seq(
    "LegacySimilo",
    "OptimizedSimilo",
    "Vista",
    "RobulaPlus",
    "Color",
    "VonSimilo",
    "Multi Locator"
  )
  // (subset label, csv file name, preferred SCORE-% column for that file)
  private val Subsets: Seq[(String, String, String)] = Seq(
    ("All Elements",  "element_summary_all.csv",   "survival_score_percent"),
    ("Minor Change",  "element_summary_minor.csv", "survival_score_in_class_percent"),
    ("Major Change",  "element_summary_major.csv", "survival_score_in_class_percent")
  )
  private val OutCsv = ResultsDir.resolve("figures/boxplot_stats_score.csv")

  // ---- Math helpers (percentiles & whiskers) ----
  private def percentile(sorted: Vector[Double], p: Double): Double = {
    if (sorted.isEmpty) return Double.NaN
    if (p <= 0.0) return sorted.head
    if (p >= 1.0) return sorted.last
    val n  = sorted.length
    val x  = p * (n - 1)
    val lo = math.floor(x).toInt
    val hi = math.ceil(x).toInt
    if (lo == hi) sorted(lo)
    else {
      val w = x - lo
      sorted(lo) * (1.0 - w) + sorted(hi) * w
    }
  }
  private def whiskers(sorted: Vector[Double], q1: Double, q3: Double): (Double, Double) = {
    if (sorted.isEmpty) return (Double.NaN, Double.NaN)
    val iqr = q3 - q1
    val lowFence  = q1 - 1.5 * iqr
    val highFence = q3 + 1.5 * iqr
    val lw = sorted.find(_ >= lowFence).getOrElse(sorted.head)
    val uw = sorted.reverse.find(_ <= highFence).getOrElse(sorted.last)
    (lw, uw)
  }
  private def mean(xs: Vector[Double]): Double =
    if (xs.isEmpty) Double.NaN else xs.sum / xs.size

  // ---- CSV IO ----
  private def readCsv(path: Path): Option[(Array[String], Vector[Array[String]])] = {
    if (!Files.exists(path)) return None
    val src = Source.fromFile(path.toFile, "UTF-8")
    try {
      val it = src.getLines()
      if (!it.hasNext) return None
      val header = it.next().split(",", -1).map(_.trim)
      val rows   = it.map(_.split(",", -1)).map(_.map(_.trim)).toVector
      Some(header -> rows)
    } finally src.close()
  }
  private def findColIndex(header: Array[String], candidates: Seq[String]): Option[Int] = {
    val idx = header.zipWithIndex.toMap
    candidates.collectFirst { case name if idx.contains(name) => idx(name) }
  }
  private def writeCsv(path: Path, header: String, rows: Iterable[Seq[Any]]): Unit = {
    Files.createDirectories(path.getParent)
    val sb = new StringBuilder
    sb.append(header).append('\n')
    rows.foreach { r => sb.append(r.map(renderCell).mkString(",")).append('\n') }
    Files.write(
      path,
      sb.toString.getBytes("UTF-8"),
      StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE
    )
  }
  private def renderCell(v: Any): String = v match {
    case d: Double if d.isNaN => ""
    case d: Double            => f"$d%.6f"
    case x                    => x.toString
  }

  // ---- Load SCORE % values for one (algorithm, subset) ----
  private def loadPercents(
                            algoLabel: String,
                            algoDirName: String,
                            csvName: String,
                            preferredScorePercentCol: String
                          ): Vector[Double] = {
    val p = ResultsDir.resolve(algoDirName).resolve(csvName)
    if (Verbose) println(s"[dbg] reading: $p for $algoLabel")
    readCsv(p) match {
      case None =>
        println(s"[warn] missing CSV: $p")
        Vector.empty
      case Some((header, rows)) =>
        // Priority: score-% columns; fallback to ratio columns if necessary
        val candidates = Seq(
          preferredScorePercentCol, // e.g., survival_score_in_class_percent
          "survival_score_percent", // all-elements file
          // fallbacks (ratios) if someone runs older exporter:
          "survival_ratio_in_class_percent",
          "survival_ratio_percent"
        )
        val idxOpt = findColIndex(header, candidates)
        idxOpt match {
          case None =>
            println(s"[warn] $p has none of ${candidates.mkString(", ")} ; found: ${header.mkString("[", ", ", "]")}")
            Vector.empty
          case Some(idx) =>
            val rawVals = rows.flatMap { arr =>
              if (idx < arr.length) {
                val txt = arr(idx).replace("%", "").trim
                scala.util.Try(txt.toDouble).toOption
              } else None
            }
            // These are percent values; keep them as 0..100 (for plotting or stats on percentage scale).
            val cleaned = rawVals.collect { case d if d.isFinite => d }.toVector
            if (Verbose) {
              val n = cleaned.size
              val (mn, mx, m) =
                if (n > 0) (cleaned.min, cleaned.max, cleaned.sum / n)
                else (Double.NaN, Double.NaN, Double.NaN)
              println(s"[dbg] parsed $n score-% for $algoLabel / $csvName ; min=$mn max=$mx mean=$m")
            }
            cleaned
        }
    }
  }

  // ---- Compute stats row ----
  private case class StatsRow(
                               algorithm: String,
                               subset:    String,
                               count:     Int,
                               mean:      Double,
                               median:    Double,
                               q1:        Double,
                               q3:        Double,
                               whiskerLo: Double,
                               whiskerHi: Double,
                               min:       Double,
                               max:       Double
                             ) {
    def asSeq: Seq[Any] =
      Seq(algorithm, subset, count, mean, median, q1, q3, whiskerLo, whiskerHi, min, max)
  }

  private def statsFor(algorithm: String, subsetLabel: String, valuesPct: Vector[Double]): StatsRow = {
    val sorted = valuesPct.sorted
    val n      = sorted.length
    if (n == 0)
      return StatsRow(algorithm, subsetLabel, 0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN)

    val q1  = percentile(sorted, 0.25)
    val med = percentile(sorted, 0.50)
    val q3  = percentile(sorted, 0.75)
    val (lw, uw) = whiskers(sorted, q1, q3)

    StatsRow(
      algorithm = algorithm,
      subset    = subsetLabel,
      count     = n,
      mean      = mean(sorted),
      median    = med,
      q1        = q1,
      q3        = q3,
      whiskerLo = lw,
      whiskerHi = uw,
      min       = sorted.head,
      max       = sorted.last
    )
  }

  private def fmt(d: Double): String =
    if (d.isNaN) "-" else f"$d%.2f"

  /** Print a human-readable summary grouped by subset (panel) and algorithm. */
  private def printHumanReadable(rows: Seq[StatsRow]): Unit = {
    val bySubset = rows.groupBy(_.subset)
    bySubset.toSeq.sortBy(_._1).foreach { case (subset, rs) =>
      println(s"\n===== $subset (Survival Score %) =====")
      val header =
        f"${"Algorithm"}%-16s ${"n"}%6s ${"mean%"}%8s ${"median%"}%9s ${"q1%"}%8s ${"q3%"}%8s ${"whiskLo%"}%10s ${"whiskHi%"}%10s ${"min%"}%8s ${"max%"}%8s"
      println(header)
      rs.sortBy(_.algorithm).foreach { r =>
        val line =
          f"${r.algorithm}%-16s ${r.count}%6d ${fmt(r.mean)}%8s ${fmt(r.median)}%9s ${fmt(r.q1)}%8s ${fmt(r.q3)}%8s ${fmt(r.whiskerLo)}%10s ${fmt(r.whiskerHi)}%10s ${fmt(r.min)}%8s ${fmt(r.max)}%8s"
        println(line)
      }
    }
    println()
  }

  def main(args: Array[String]): Unit = {
    val buf = Vector.newBuilder[StatsRow]
    for {
      algo <- Algorithms
      (subsetLabel, csvName, scorePctCol) <- Subsets
    } {
      val valuesPct = loadPercents(algo, algo, csvName, scorePctCol)
      val row = statsFor(algo, subsetLabel, valuesPct)
      if (Verbose) println(s"[dbg] stats $algo | $subsetLabel -> n=${row.count} mean=${row.mean} median=${row.median}")
      buf += row
    }
    val rows = buf.result()

    // Pretty console dump (easy to paste into the thesis)
    printHumanReadable(rows)

    val header = "algorithm,subset,count,mean_percent,median_percent,q1_percent,q3_percent,whisker_lo_percent,whisker_hi_percent,min_percent,max_percent"
    writeCsv(OutCsv, header, rows.map(_.asSeq))
    println(s"\n[ok] boxplot score% stats written to: $OutCsv")
  }
}