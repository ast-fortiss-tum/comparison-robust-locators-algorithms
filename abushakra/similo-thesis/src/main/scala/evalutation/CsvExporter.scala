package evalutation

import evalutation.EvaluationStatistics._
import evalutation.Classification.ElementClassification
import evalutation.EvaluationStatistics.MatchType.ExactMatch
import elements.Element

import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.LocalDate
import java.util.Locale

object CsvExporter {

  /** Escape for a simple CSV; keeps it dependency-free. */
  private def esc(s: String): String = {
    if (s == null) "\"\""
    else {
      val needs = s.exists(ch => ch == ',' || ch == '"' || ch == '\n' || ch == '\r')
      if (!needs) s
      else "\"" + s.replace("\"", "\"\"") + "\""
    }
  }

  private val US = Locale.US

  private def writeCsv(path: Path, header: String, rows: Iterable[Seq[Any]]): Unit = {
    Files.createDirectories(path.getParent)
    val sb = new StringBuilder
    sb.append(header).append("\n")
    rows.foreach { r =>
      sb.append(r.map {
        case d: Double        => String.format(US, "%.2f", d) // dot as decimal separator
        case ld: LocalDate    => ld.toString
        case b: Boolean       => if (b) "true" else "false"
        case s: String        => esc(s)
        case x                => esc(String.valueOf(x))
      }.mkString(",")).append("\n")
    }
    Files.write(
      path,
      sb.toString.getBytes("UTF-8"),
      StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE
    )
  }

  private def elemId(e: Element): String =
    Option(e).flatMap(_.xpath match {
      case x if x != null => Option(x.chrome)
      case _ => None
    }).getOrElse("-")

  /** Export four CSVs summarising survivability. Call once per algorithm. */
  def exportAll(algorithmName: String, stats: EvaluationStatistics, outDir: Path): Unit = {
    exportElementSummary(algorithmName, stats, outDir.resolve("element_summary_all.csv"))
    exportElementSummaryFiltered(algorithmName, stats, outDir.resolve("element_summary_minor.csv"), ElementClassification.MINOR_CHANGE)
    exportElementSummaryFiltered(algorithmName, stats, outDir.resolve("element_summary_major.csv"), ElementClassification.MAJOR_CHANGE)
    exportMaxStreakPerSite(algorithmName, stats, outDir.resolve("site_max_streak.csv"))
    exportSiteEndResults(algorithmName, stats, outDir.resolve("site_end_results.csv"))
  }

  /** 2) One row per (site, element) with survival ratio (+ %) and Survival Score (+ %). */
  def exportElementSummary(algorithm: String, stats: EvaluationStatistics, out: Path): Unit = {
    // helpers
    def untilDeleted(tl: Seq[EvaluationStatistics.Result]): Seq[EvaluationStatistics.Result] = {
      val ordered = tl.sortBy(_.date)
      val stopIdx = ordered.indexWhere(r => !r.existsOnNewVersion)
      if (stopIdx == -1) ordered else ordered.take(stopIdx) // exclude the deleted record
    }
    def matchMult(r: EvaluationStatistics.Result): Int =
      r.found.headOption.map(_._2) match {
        case Some(ExactMatch)                               => 4
        case Some(EvaluationStatistics.MatchType.InOverlap) => 1
        case _                                              => 0
      }
    def survivalScores(tl: Seq[EvaluationStatistics.Result]): (Double, Double) = {
      val win = untilDeleted(tl)
      val n   = win.size
      if (n == 0) return (0.0, 0.0)
      // Time weights: later versions contribute more (t/N)
      val weights = (1 to n).map(i => i.toDouble / n.toDouble)
      val raw = win.zip(weights).map { case (r, w) =>
        (r.classificationScore.toDouble * matchMult(r)) * w
      }.sum
      val max = win.zip(weights).map { case (r, w) =>
        (r.classificationScore.toDouble * 4.0) * w
      }.sum
      val pct = if (max > 0)
        BigDecimal(raw / max * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
      else 0.0
      (raw, pct)
    }

    val grouped =
      for {
        ElementLocalisation(site, siteResults) <- stats.results
        byElem = siteResults.groupBy(_.toFind).view.mapValues(_.sortBy(_.date)).toMap
      } yield (site -> byElem)

    val rows = grouped.flatMap { case (site, byElem) =>
      byElem.map { case (elem, timeline) =>
        val ordered         = timeline.sortBy(_.date)
        val versionsTotal   = ordered.size
        val existsCount     = ordered.count(_.existsOnNewVersion)
        val exactCount      = ordered.count(_.found.headOption.exists(_._2 == ExactMatch))

        // classic survivability (ratio + %), overall (not class-filtered)
        val survRatio       = if (existsCount > 0) exactCount.toDouble / existsCount.toDouble else 0.0
        val survRatioPct    = BigDecimal(survRatio * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

        // Survival Score (stops at deletion window, uses classificationScore × match multiplier)
        val (survScoreRaw, survScoreMax) = {
          val win = untilDeleted(ordered)
          val n = win.size
          if (n == 0) (0.0, 0.0)
          else {
            val weights = (1 to n).map(i => i.toDouble / n.toDouble)
            val raw = win.zip(weights).map { case (r, w) =>
              (r.classificationScore.toDouble * matchMult(r)) * w
            }.sum
            val max = win.zip(weights).map { case (r, w) =>
              (r.classificationScore.toDouble * 4.0) * w
            }.sum
            (raw, max)
          }
        }

        Seq(
          algorithm,
          site,
          elemId(elem),
          versionsTotal,    // versions_total
          existsCount,      // versions_existing
          exactCount,       // exact_matches
          survRatio,        // survival_ratio
          survRatioPct,     // survival_ratio_percent
          survScoreRaw,     // survival_score_raw
          survScoreMax      // survival_score_max
        )
      }
    }

    val header =
      "algorithm,site,original_xpath," +
        "versions_total,versions_existing,exact_matches," +
        "survival_ratio,survival_ratio_percent," +
        "survival_score_raw,survival_score_max"

    writeCsv(out, header, rows)
  }

  /** Same as element_summary, but filtered by a specific ElementClassification; includes
   * class-specific survival ratio (+ %) and Survival Score (+ %). */
  def exportElementSummaryFiltered(
                                    algorithm: String,
                                    stats: EvaluationStatistics,
                                    out: Path,
                                    classFilter: Classification.ElementClassification
                                  ): Unit = {

    // shared helpers
    def untilDeleted(tl: Seq[EvaluationStatistics.Result]): Seq[EvaluationStatistics.Result] = {
      val ordered = tl.sortBy(_.date)
      val stopIdx = ordered.indexWhere(r => !r.existsOnNewVersion)
      if (stopIdx == -1) ordered else ordered.take(stopIdx)
    }
    def matchMult(r: EvaluationStatistics.Result): Int =
      r.found.headOption.map(_._2) match {
        case Some(ExactMatch)                               => 4
        case Some(EvaluationStatistics.MatchType.InOverlap) => 1
        case _                                              => 0
      }
    def survivalScoresForClass(tl: Seq[EvaluationStatistics.Result]): (Double, Double) = {
      val win = untilDeleted(tl)
      val n   = win.size
      if (n == 0) return (0.0, 0.0)
      // Time weights on the full window; we then select only class rows but keep their weights
      val weights = (1 to n).map(i => i.toDouble / n.toDouble)
      val pairs   = win.zip(weights)
      val inClass = pairs.collect { case (r, w) if r.classification.element == classFilter => (r, w) }
      val raw = inClass.map { case (r, w) =>
        (r.classificationScore.toDouble * matchMult(r)) * w
      }.sum
      val max = inClass.map { case (r, w) =>
        (r.classificationScore.toDouble * 4.0) * w
      }.sum
      val pct = if (max > 0)
        BigDecimal(raw / max * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
      else 0.0
      (raw, pct)
    }

    val rows =
      for {
        EvaluationStatistics.ElementLocalisation(site, siteResults) <- stats.results
        (elem, timeline) <- siteResults.groupBy(_.toFind).toSeq
        if timeline.exists(_.classification.element == classFilter) // element ever had this class
      } yield {
        val ordered               = timeline.sortBy(_.date)
        val versionsTotal         = ordered.size
        val existsCountAll        = ordered.count(_.existsOnNewVersion) // reference only

        // class-specific counts (not censored beyond deletion window for ratio)
        val inClassAll            = ordered.filter(_.classification.element == classFilter)
        val existsCountClass      = inClassAll.count(_.existsOnNewVersion)
        val exactCountClass       = inClassAll.count(_.found.headOption.exists(_._2 == ExactMatch))

        val survRatioClass        = if (existsCountClass > 0)
          exactCountClass.toDouble / existsCountClass.toDouble else 0.0
        val survRatioClassPct     = BigDecimal(survRatioClass * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

        Seq(
          algorithm,
          site,
          elemId(elem),
          versionsTotal,         // versions_total
          existsCountAll,        // versions_existing (overall; reference)
          existsCountClass,      // versions_existing_in_class
          exactCountClass,       // exact_matches_in_class
          survRatioClass,        // survival_ratio_in_class
          survRatioClassPct      // survival_ratio_in_class_percent
        )
      }

    val header =
      "algorithm,site,original_xpath," +
        "versions_total,versions_existing," +
        "versions_existing_in_class,exact_matches_in_class," +
        "survival_ratio_in_class,survival_ratio_in_class_percent"

    writeCsv(out, header, rows)
  }

  /** 4) One row per site: maximum consecutive ExactMatch streak among its elements. */
  def exportMaxStreakPerSite(algorithm: String, stats: EvaluationStatistics, out: Path): Unit = {
    val rows =
      for {
        ElementLocalisation(site, siteResults) <- stats.results
      } yield {
        val byElem = siteResults.groupBy(_.toFind).view.mapValues(_.sortBy(_.date)).toMap
        val maxRunForSite =
          byElem.values.map { timeline =>
            timeline.foldLeft((0, 0)) { case ((best, cur), r) =>
              val next = if (r.found.headOption.exists(_._2 == ExactMatch)) cur + 1 else 0
              (best.max(next), next)
            }._1
          }.foldLeft(0)(_ max _)

        Seq(algorithm, site, maxRunForSite)
      }

    val header = "algorithm,site,max_consecutive_exact"
    writeCsv(out, header, rows)
  }

  /** One row per site:
   *  - Survival Score (RAW) + %  for all / minor-only / major-only
   *  - Classic survivability ratios (ratio + %) for all / minor / major
   *  - Appends an OVERALL row averaging all sites
   */
  def exportSiteEndResults(
                            algorithm: String,
                            stats: EvaluationStatistics,
                            out: java.nio.file.Path
                          ): Unit = {
    import evalutation.Classification.ElementClassification
    import evalutation.Classification.LocatorClassification
    import evalutation.EvaluationStatistics.MatchType

    // ----- Helpers for Survival Score (window stops at deletion) -----
    def untilDeleted(tl: Seq[EvaluationStatistics.Result]): Seq[EvaluationStatistics.Result] = {
      val ordered = tl.sortBy(_.date)
      val stopIdx = ordered.indexWhere(r => !r.existsOnNewVersion)
      if (stopIdx == -1) ordered else ordered.take(stopIdx)
    }
    def matchMult(r: EvaluationStatistics.Result): Int =
      r.found.headOption.map(_._2) match {
        case Some(MatchType.ExactMatch)     => 4
        case Some(MatchType.InOverlap)      => 1
        case _                              => 0
      }

    // Survival Score RAW and % for ALL
    def scoreRawAll(tl: Seq[EvaluationStatistics.Result]): Double = {
      val win = untilDeleted(tl)
      val n   = win.size
      if (n == 0) 0.0
      else {
        val weights = (1 to n).map(i => i.toDouble / n.toDouble)
        win.zip(weights).map { case (r, w) => (r.classificationScore.toDouble * matchMult(r)) * w }.sum
      }
    }
    def scorePctAll(tl: Seq[EvaluationStatistics.Result]): Double = {
      val win = untilDeleted(tl)
      val n   = win.size
      if (n == 0) 0.0
      else {
        val weights = (1 to n).map(i => i.toDouble / n.toDouble)
        val raw = win.zip(weights).map { case (r, w) => (r.classificationScore.toDouble * matchMult(r)) * w }.sum
        val max = win.zip(weights).map { case (r, w) => (r.classificationScore.toDouble * 4.0) * w }.sum
        if (max > 0) BigDecimal(raw / max * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble else 0.0
      }
    }

    // Survival Score RAW and % for a specific class
    def scoreRawForClass(tl: Seq[EvaluationStatistics.Result], cls: ElementClassification): Double = {
      val win = untilDeleted(tl)
      val n   = win.size
      if (n == 0) 0.0
      else {
        val weights = (1 to n).map(i => i.toDouble / n.toDouble)
        win.zip(weights).collect { case (r, w) if r.classification.element == cls => (r, w) }
          .map { case (r, w) => (r.classificationScore.toDouble * matchMult(r)) * w }.sum
      }
    }
    def scorePctForClass(tl: Seq[EvaluationStatistics.Result], cls: ElementClassification): Double = {
      val win = untilDeleted(tl)
      val n   = win.size
      if (n == 0) 0.0
      else {
        val weights = (1 to n).map(i => i.toDouble / n.toDouble)
        val inClass = win.zip(weights).collect { case (r, w) if r.classification.element == cls => (r, w) }
        val raw = inClass.map { case (r, w) => (r.classificationScore.toDouble * matchMult(r)) * w }.sum
        val max = inClass.map { case (r, w) => (r.classificationScore.toDouble * 4.0) * w }.sum
        if (max > 0) BigDecimal(raw / max * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble else 0.0
      }
    }

    // ----- Helpers for classic survivability ratios (over full timeline) -----
    def ratioAll(tl: Seq[EvaluationStatistics.Result]): Double = {
      val ordered     = tl.sortBy(_.date)
      val existsCount = ordered.count(_.existsOnNewVersion)
      if (existsCount == 0) 0.0
      else {
        val exactCount = ordered.count(_.found.headOption.exists(_._2 == MatchType.ExactMatch))
        exactCount.toDouble / existsCount.toDouble
      }
    }
    def ratioForClass(tl: Seq[EvaluationStatistics.Result], cls: ElementClassification): Double = {
      val ordered     = tl.sortBy(_.date).filter(_.classification.element == cls)
      val existsCount = ordered.count(_.existsOnNewVersion)
      if (existsCount == 0) 0.0
      else {
        val exactCount = ordered.count(_.found.headOption.exists(_._2 == MatchType.ExactMatch))
        exactCount.toDouble / existsCount.toDouble
      }
    }

    // Average across *elements* in a site for any per-timeline measure
    def avgOfSite(siteResults: Seq[EvaluationStatistics.Result])(
      f: Seq[EvaluationStatistics.Result] => Double
    ): Double = {
      val byElem  = siteResults.groupBy(_.toFind).view.mapValues(_.sortBy(_.date)).toMap.values.toSeq
      val perElem = byElem.map(f)
      if (perElem.nonEmpty) BigDecimal(perElem.sum / perElem.size).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble else 0.0
    }

    val bySite = stats.results.groupBy(_.site).view.mapValues(_.flatMap(_.results)).toMap

    // Build a structured list first so we can also compute an OVERALL row easily
    // For OVERALL, we want: sum of all survival_score_raw and survival_score_max (across all elements), and percent
    // For sites: show sum of survival_score_raw and survival_score_max (across all elements in the site), but do not compute percent for survival score
    // Site rows now show sums, not averages, for survival scores.
    /** Row for site or overall summary; locatorBreakFoundPercent is always defined (site and overall). */
    case class Row(
      algorithm: String,
      site: String,
      survivalScoreRaw: Double,
      survivalScoreMax: Double,
      survivalScorePercent: Option[Double], // Only for OVERALL, None for sites
      avgRatioAll: Double,
      avgRatioAllPct: Double,
      avgRatioMinor: Double,
      avgRatioMinorPct: Double,
      avgRatioMajor: Double,
      avgRatioMajorPct: Double,
      locatorBreakSuccessCount: Int,
      locatorBreakTotal: Int,
      locatorBreakFoundPercent: Double // Always defined for site and overall
    )

    val siteRows: Seq[Row] = bySite.toSeq.sortBy(_._1).map { case (site, siteResults) =>
      // Group siteResults by element
      val byElem: Seq[Seq[EvaluationStatistics.Result]] = siteResults.groupBy(_.toFind).values.toSeq
      // Compute sum of raw and max survival scores for all elements in this site
      val sumScoreRawAll: Double = byElem.map(scoreRawAll).sum
      val sumScoreMaxAll: Double = byElem.map { tl =>
        val win = untilDeleted(tl)
        val n   = win.size
        if (n == 0) 0.0
        else {
          val weights = (1 to n).map(i => i.toDouble / n.toDouble)
          win.zip(weights).map { case (r, w) => (r.classificationScore.toDouble * 4.0) * w }.sum
        }
      }.sum

      // Classic survivability ratios (still averaged across elements)
      val avgRatioAll        = avgOfSite(siteResults)(ratioAll)
      val avgRatioAllPct     = BigDecimal(avgRatioAll * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

      val avgRatioMinor      = avgOfSite(siteResults)(tl => ratioForClass(tl, ElementClassification.MINOR_CHANGE))
      val avgRatioMinorPct   = BigDecimal(avgRatioMinor * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

      val avgRatioMajor      = avgOfSite(siteResults)(tl => ratioForClass(tl, ElementClassification.MAJOR_CHANGE))
      val avgRatioMajorPct   = BigDecimal(avgRatioMajor * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble

      // Locator breakage stats (LOCATOR_CHANGED)
      val locatorChangeResults = siteResults.filter(_.classification.locator == LocatorClassification.LOCATOR_CHANGED)
      val locatorBreakSuccessCount = locatorChangeResults.count(_.exactMatch)
      val locatorBreakTotal        = locatorChangeResults.size
      val locatorBreakFoundPercent =
        if (locatorBreakTotal > 0)
          BigDecimal(locatorBreakSuccessCount.toDouble / locatorBreakTotal.toDouble * 100)
            .setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
        else 0.0

      Row(
        algorithm, site,
        sumScoreRawAll,   // site sum (not average)
        sumScoreMaxAll,   // site sum (not average)
        None,             // percent only for OVERALL
        avgRatioAll,      avgRatioAllPct,
        avgRatioMinor,    avgRatioMinorPct,
        avgRatioMajor,    avgRatioMajorPct,
        locatorBreakSuccessCount,
        locatorBreakTotal,
        locatorBreakFoundPercent
      )
    }

    // Compute OVERALL row: sum all raw and max across all elements, then compute percent for survival_score_all, average others
    def avg(xs: Seq[Double]): Double =
      if (xs.nonEmpty) BigDecimal(xs.sum / xs.size).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble else 0.0

    // Overall locator-break stats (sum over sites)
    val totalLocatorBreakSuccess = siteRows.map(_.locatorBreakSuccessCount).sum
    val totalLocatorBreaks       = siteRows.map(_.locatorBreakTotal).sum
    val overallLocatorBreakFoundPercent =
      if (totalLocatorBreaks > 0)
        BigDecimal(totalLocatorBreakSuccess.toDouble / totalLocatorBreaks.toDouble * 100)
          .setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
      else 0.0

    // To get sumRaw and sumMax, we need to sum over all elements (not site averages)
    val allElementResults: Seq[Seq[EvaluationStatistics.Result]] =
      bySite.values.flatMap { siteResults =>
        siteResults.groupBy(_.toFind).values.toSeq
      }.toSeq

    val totalSurvivalScoreRaw: Double = allElementResults.map(scoreRawAll).sum
    val totalSurvivalScoreMax: Double = allElementResults.map { tl =>
      val win = untilDeleted(tl)
      val n   = win.size
      if (n == 0) 0.0
      else {
        val weights = (1 to n).map(i => i.toDouble / n.toDouble)
        win.zip(weights).map { case (r, w) => (r.classificationScore.toDouble * 4.0) * w }.sum
      }
    }.sum
    val survivalScorePercent: Double =
      if (totalSurvivalScoreMax > 0)
        BigDecimal(totalSurvivalScoreRaw / totalSurvivalScoreMax * 100).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
      else 0.0

    val overall = Row(
      algorithm = algorithm,
      site = "OVERALL",
      survivalScoreRaw   = totalSurvivalScoreRaw,
      survivalScoreMax   = totalSurvivalScoreMax,
      survivalScorePercent = Some(survivalScorePercent),
      avgRatioAll      = avg(siteRows.map(_.avgRatioAll)),
      avgRatioAllPct   = avg(siteRows.map(_.avgRatioAllPct)),
      avgRatioMinor    = avg(siteRows.map(_.avgRatioMinor)),
      avgRatioMinorPct = avg(siteRows.map(_.avgRatioMinorPct)),
      avgRatioMajor    = avg(siteRows.map(_.avgRatioMajor)),
      avgRatioMajorPct = avg(siteRows.map(_.avgRatioMajorPct)),
      locatorBreakSuccessCount = totalLocatorBreakSuccess,
      locatorBreakTotal        = totalLocatorBreaks,
      locatorBreakFoundPercent = overallLocatorBreakFoundPercent
    )

    val rowsAsSeqs: Seq[Seq[Any]] = (siteRows :+ overall).map { r =>
      Seq(
        r.algorithm, r.site,
        r.survivalScoreRaw, r.survivalScoreMax, r.survivalScorePercent.getOrElse(""),
        r.avgRatioAll, r.avgRatioAllPct,
        r.avgRatioMinor, r.avgRatioMinorPct,
        r.avgRatioMajor, r.avgRatioMajorPct,
        r.locatorBreakSuccessCount, r.locatorBreakTotal, r.locatorBreakFoundPercent
      )
    }

    val header =
      "algorithm,site," +
        "total_survival_score_raw,total_survival_score_max,survival_score_percent," +
        "avg_survival_ratio_all,avg_survival_ratio_all_percent," +
        "avg_survival_ratio_minor,avg_survival_ratio_minor_percent," +
        "avg_survival_ratio_major,avg_survival_ratio_major_percent," +
        "locator_break_success_count,locator_break_total,locator_break_found_percent"

    writeCsv(out, header, rowsAsSeqs)
  }
}