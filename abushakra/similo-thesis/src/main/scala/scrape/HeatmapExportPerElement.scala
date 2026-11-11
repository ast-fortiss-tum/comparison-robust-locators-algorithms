//package scrape
//
//import evalutation.EvaluationStatistics
//import evalutation.EvaluationStatistics.{MatchType, Result}
//import evalutation.Classification.ElementClassification
//import java.nio.file.{Files, Path, Paths}
//import java.nio.charset.StandardCharsets
//import java.time.LocalDate
//import scala.util.Try
//import scala.io.Source
//
//object HeatmapExportPerElement {
//
//  // ---- Cell codes (unchanged) ----
//  object Cell {
//    val Exact   = 2       // top-1 is exact target
//    val Overlap = 1       // top-1 lies within target’s visual overlap
//    val Miss    = 0       // exists on new version, but not found
//    val Deleted = 3       // dataset says element deleted on that version
//    val NA      = -1      // no data for this algo/version
//  }
//
//  /** A single algorithm row for one element: pretty name + one int per version/date */
//  final case class AlgoRow(algorithm: String, values: Vector[Int])
//
//  /** A single algorithm row for one element: string labels per version/date (SAME/MINOR/MAJOR/DELETED/blank) */
//  final case class ClassRow(algorithm: String, values: Vector[String])
//
//  /** Public API: write per‑element CSVs under outDir/<site>/<elementKey>.csv (+ .classes.csv) */
//  def save(all: Seq[(String, EvaluationStatistics)], outDir: Path): Unit = {
//    if (!Files.exists(outDir)) Files.createDirectories(outDir)
//
//    val sites: Vector[String] =
//      all.flatMap(_._2.results.map(_.site)).distinct.sorted.toVector
//
//    sites.foreach { site =>
//      val dates  = readAllSiteDates(site).getOrElse(collectSiteDatesFromResults(all, site))
//      val header = Vector("algorithm") ++ dates.map(_.toString)
//
//      // One matrix per algorithm for this site: elementKey -> Map[date -> code/label]
//      val perAlgoMatrices: Seq[(String, Map[String, Map[LocalDate, Int]])] =
//        all.map { case (algoName, stats) =>
//          algoName -> collectSiteMatrix(stats, site)
//        }
//
//      val perAlgoClasses: Seq[(String, Map[String, Map[LocalDate, String]])] =
//        all.map { case (algoName, stats) =>
//          algoName -> collectSiteClassMatrix(stats, site)
//        }
//
//      // Union of all element keys observed by any algorithm on this site
//      val allElementKeys: Vector[String] =
//        (perAlgoMatrices.flatMap(_._2.keys) ++ perAlgoClasses.flatMap(_._2.keys))
//          .distinct
//          .sorted
//          .toVector
//
//      // Write one pair of CSVs per element
//      allElementKeys.zipWithIndex.foreach { case (elementKey, idx) =>
//        val rows: Seq[AlgoRow] =
//          perAlgoMatrices.map { case (algoName, matrix) =>
//            val rowValues: Vector[Int] =
//              dates.map { d =>
//                matrix
//                  .get(elementKey)          // Map[date -> code]
//                  .flatMap(_.get(d))
//                  .getOrElse(Cell.NA)
//              }.toVector
//            AlgoRow(algoName, rowValues)
//          }
//
//        val classRows: Seq[ClassRow] =
//          perAlgoClasses.map { case (algoName, matrix) =>
//            val rowValues: Vector[String] =
//              dates.map { d =>
//                matrix
//                  .get(elementKey)          // Map[date -> label]
//                  .flatMap(_.get(d))
//                  .getOrElse("")            // blank = no label/NA
//              }.toVector
//            ClassRow(algoName, rowValues)
//          }
//
//        val base = outDir
//          .resolve(site)
//          .resolve(s"${safeName(elementKey, idx)}")
//
//        // numeric matrix
//        writeCsvInts(header, rows, base.resolveSibling(base.getFileName.toString + ".csv"), Some(elementKey))
//        // labels matrix
//        writeCsvStrings(header, classRows, base.resolveSibling(base.getFileName.toString + ".classes.csv"), Some(elementKey))
//      }
//    }
//  }
//
//  // ---------- Helpers ----------
//
//  /** Try to read all expected dates for a site from sites/<site>/urls.csv. */
//  private def readAllSiteDates(site: String): Option[Vector[LocalDate]] = {
//    val p = Paths.get("sites", site, "urls.csv")
//    if (!Files.exists(p)) return None
//    val src = Source.fromFile(p.toFile, "UTF-8")
//    try {
//      val lines = src.getLines().toVector
//      if (lines.isEmpty) return None
//      val dataLines =
//        if (lines.head.toLowerCase.startsWith("date")) lines.tail else lines // skip header if present
//      val dates = dataLines.flatMap { ln =>
//        val sep = if (ln.contains(";") && !ln.contains(",")) ";" else ","
//        val parts = ln.split(sep, 2)
//        if (parts.nonEmpty) {
//          Try(LocalDate.parse(parts(0).trim)).toOption
//        } else None
//      }.distinct.sorted.toVector
//      if (dates.nonEmpty) Some(dates) else None
//    } catch {
//      case _: Throwable => None
//    } finally {
//      src.close()
//    }
//  }
//
//  /** Previous behavior: build date axis per site from results only (fallback). */
//  private def collectSiteDatesFromResults(all: Seq[(String, EvaluationStatistics)], site: String): Vector[LocalDate] =
//    all
//      .flatMap { case (_, stats) =>
//        stats.results
//          .filter(_.site == site)
//          .flatMap(_.results.map(_.date))
//      }
//      .distinct
//      .sorted
//      .toVector
//
//  /** Encode one Result into a cell value driven by dataset classification (not by algo failure). */
//  private def encode(res: Result): Int = {
//    if (res.classification.element == ElementClassification.DELETED) Cell.Deleted
//    else res.found.headOption match {
//      case Some((_, MatchType.ExactMatch, _)) => Cell.Exact
//      case Some((_, MatchType.InOverlap, _))  => Cell.Overlap
//      case _                                  => Cell.Miss
//    }
//  }
//
//  /** Label for change type in each cell (SAME/MINOR/MAJOR/DELETED or "") */
//  private def labelOf(res: Result): String = res.classification.element match {
//    case ElementClassification.SAME          => "SAME"
//    case ElementClassification.MINOR_CHANGE  => "MINOR"
//    case ElementClassification.MAJOR_CHANGE  => "MAJOR"
//    case ElementClassification.DELETED       => "DELETED"
//    case _                                   => ""
//  }
//
//  /**
//   * For one algorithm + site, build numeric matrix:
//   *   key = the v1 (toFind) absolute xpath → Map[date -> cell-code].
//   * This keeps element identity stable across versions.
//   */
//  private def collectSiteMatrix(stats: EvaluationStatistics, site: String)
//  : Map[String, Map[LocalDate, Int]] = {
//    val siteResults: Seq[Result] =
//      stats.results
//        .filter(_.site == site)
//        .flatMap(_.results)
//
//    siteResults
//      .groupBy(_.toFind.xpath.chrome)
//      .view
//      .mapValues { rs =>
//        rs.map(r => r.date -> encode(r)).toMap
//      }
//      .toMap
//  }
//
//  /**
//   * For one algorithm + site, build label matrix:
//   *   key = the v1 (toFind) absolute xpath → Map[date -> "SAME"/"MINOR"/"MAJOR"/"DELETED"/""].
//   */
//  private def collectSiteClassMatrix(stats: EvaluationStatistics, site: String)
//  : Map[String, Map[LocalDate, String]] = {
//    val siteResults: Seq[Result] =
//      stats.results
//        .filter(_.site == site)
//        .flatMap(_.results)
//
//    siteResults
//      .groupBy(_.toFind.xpath.chrome)
//      .view
//      .mapValues { rs =>
//        rs.map(r => r.date -> labelOf(r)).toMap
//      }
//      .toMap
//  }
//
//  /** Write semicolon-separated CSV (ints): optional first line with xpath meta. */
//  private def writeCsvInts(header: Vector[String], rows: Seq[AlgoRow], out: Path, xpathMeta: Option[String]): Unit = {
//    if (!Files.exists(out.getParent)) Files.createDirectories(out.getParent)
//    val headerLine = header.mkString(";")
//    val body = rows.map(r => (r.algorithm +: r.values.map(_.toString)).mkString(";"))
//    val meta = xpathMeta.map(xp => s"# XPATH=$xp\n").getOrElse("")
//    val csv = (meta + (headerLine +: body).mkString("\n"))
//    Files.write(out, csv.getBytes(StandardCharsets.UTF_8))
//  }
//
//  /** Write semicolon-separated CSV (strings): optional first line with xpath meta. */
//  private def writeCsvStrings(header: Vector[String], rows: Seq[ClassRow], out: Path, xpathMeta: Option[String]): Unit = {
//    if (!Files.exists(out.getParent)) Files.createDirectories(out.getParent)
//    val headerLine = header.mkString(";")
//    val body = rows.map(r => (r.algorithm +: r.values).mkString(";"))
//    val meta = xpathMeta.map(xp => s"# XPATH=$xp\n").getOrElse("")
//    val csv = (meta + (headerLine +: body).mkString("\n"))
//    Files.write(out, csv.getBytes(StandardCharsets.UTF_8))
//  }
//
//  /** Sanitize very long/ugly element keys into safe file names; use index fallback to keep unique. */
//  private def safeName(key: String, idx: Int): String = {
//    val base = key.replaceAll("[^a-zA-Z0-9._-]", "_")
//    val trimmed = if (base.length <= 120) base else base.take(120)
//    val suffix = f"_$idx%04d"
//    trimmed + suffix
//  }
//}
//
package scrape

import evalutation.EvaluationStatistics
import evalutation.EvaluationStatistics.{MatchType, Result}
import evalutation.Classification.ElementClassification
import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import scala.util.Try
import scala.io.Source

object HeatmapExportPerElement {

  // ---- Cell codes (unchanged) ----
  object Cell {
    val Exact   = 2       // top-1 is exact target
    val Overlap = 1       // top-1 lies within target’s visual overlap
    val Miss    = 0       // exists on new version, but not found
    val Deleted = 3       // dataset says element deleted on that version
    val NA      = -1      // no data for this algo/version
  }

  /** A single algorithm row for one element: pretty name + one int per version/date */
  final case class AlgoRow(algorithm: String, values: Vector[Int])

  /** A single algorithm row for one element: string labels per version/date (SAME/MINOR/MAJOR/DELETED/blank) */
  final case class ClassRow(algorithm: String, values: Vector[String])

  /** Public API: write per‑element CSVs under outDir/<site>/<elementKey>.csv (+ .classes.csv) */
  def save(all: Seq[(String, EvaluationStatistics)], outDir: Path): Unit = {
    if (!Files.exists(outDir)) Files.createDirectories(outDir)

    val sites: Vector[String] =
      all.flatMap(_._2.results.map(_.site)).distinct.sorted.toVector

    sites.foreach { site =>
      val dates  = collectSiteDatesFromResults(all, site) //val dates  = readAllSiteDates(site).getOrElse(collectSiteDatesFromResults(all, site))
      val header = Vector("algorithm") ++ dates.map(_.toString)

      // One matrix per algorithm for this site: elementKey -> Map[date -> code/label]
      val perAlgoMatrices: Seq[(String, Map[String, Map[LocalDate, Int]])] =
        all.map { case (algoName, stats) =>
          algoName -> collectSiteMatrix(stats, site)
        }

      val perAlgoClasses: Seq[(String, Map[String, Map[LocalDate, String]])] =
        all.map { case (algoName, stats) =>
          algoName -> collectSiteClassMatrix(stats, site)
        }

      // Union of all element keys observed by any algorithm on this site
      val allElementKeys: Vector[String] =
        (perAlgoMatrices.flatMap(_._2.keys) ++ perAlgoClasses.flatMap(_._2.keys))
          .distinct
          .sorted
          .toVector

      // Write one pair of CSVs per element
      allElementKeys.zipWithIndex.foreach { case (elementKey, idx) =>
        val rows: Seq[AlgoRow] =
          perAlgoMatrices.map { case (algoName, matrix) =>
            val rowValues: Vector[Int] =
              dates.map { d =>
                matrix
                  .get(elementKey)          // Map[date -> code]
                  .flatMap(_.get(d))
                  .getOrElse(Cell.NA)
              }.toVector
            AlgoRow(algoName, rowValues)
          }

        val classRows: Seq[ClassRow] =
          perAlgoClasses.map { case (algoName, matrix) =>
            val rowValues: Vector[String] =
              dates.map { d =>
                matrix
                  .get(elementKey)          // Map[date -> label]
                  .flatMap(_.get(d))
                  .getOrElse("")            // blank = no label/NA
              }.toVector
            ClassRow(algoName, rowValues)
          }

        val base = outDir
          .resolve(site)
          .resolve(s"${safeName(elementKey, idx)}")

        // numeric matrix
        writeCsvInts(header, rows, base.resolveSibling(base.getFileName.toString + ".csv"), Some(elementKey))
        // labels matrix
        writeCsvStrings(header, classRows, base.resolveSibling(base.getFileName.toString + ".classes.csv"), Some(elementKey))
      }
    }
  }

  // ---------- Helpers ----------

  /** Try to read all expected dates for a site from sites/<site>/urls.csv. */
  private def readAllSiteDates(site: String): Option[Vector[LocalDate]] = {
    val p = Paths.get("sites", site, "urls.csv")
    if (!Files.exists(p)) return None
    val src = Source.fromFile(p.toFile, "UTF-8")
    try {
      val lines = src.getLines().toVector
      if (lines.isEmpty) return None
      val dataLines =
        if (lines.head.toLowerCase.startsWith("date")) lines.tail else lines // skip header if present
      val dates = dataLines.flatMap { ln =>
        val sep = if (ln.contains(";") && !ln.contains(",")) ";" else ","
        val parts = ln.split(sep, 2)
        if (parts.nonEmpty) {
          Try(LocalDate.parse(parts(0).trim)).toOption
        } else None
      }.distinct.sorted.toVector
      if (dates.nonEmpty) Some(dates) else None
    } catch {
      case _: Throwable => None
    } finally {
      src.close()
    }
  }

  /** Previous behavior: build date axis per site from results only (fallback). */
  private def collectSiteDatesFromResults(all: Seq[(String, EvaluationStatistics)], site: String): Vector[LocalDate] =
    all
      .flatMap { case (_, stats) =>
        stats.results
          .filter(_.site == site)
          .flatMap(_.results.map(_.date))
      }
      .distinct
      .sorted
      .toVector

  // --- Robustness key stabilization: collapse v_i -> v_{i+1} chains to a single root key ---
  private case class Edge(oldKey: String, newKey: String)

  /** Build old->new edges for one site from results (uses dataset ground truth target). */
  private def buildEdges(siteResults: Seq[Result]): Seq[Edge] =
    siteResults.collect {
      // keep only transitions where the element exists on the new version
      case r if r.existsOnNewVersion =>
        val oldKey = r.toFind.xpath.chrome
        val newKey = r.target.xpath.chrome  // <-- if your Result uses a different field name for the new element, change here
        Edge(oldKey, newKey)
    }

  /** Create a resolver that maps any key to its earliest ancestor (root). */
  private def makeRootResolver(edges: Seq[Edge]): String => String = {
    // Prefer a single parent per child (newKey -> oldKey). If multiple, first wins deterministically via groupMapReduce.
    val parent: Map[String, String] =
      edges.groupMapReduce(_.newKey)(_.oldKey)((a, _) => a)

    // Path-compressed root lookup
    def root(k: String): String = {
      var x = k
      var seen = Set.empty[String]
      while (parent.contains(x) && !seen(x)) { seen += x; x = parent(x) }
      x
    }
    root
  }

  /** Encode one Result into a cell value driven by dataset classification (not by algo failure). */
  private def encode(res: Result): Int = {
    if (res.classification.element == ElementClassification.DELETED) Cell.Deleted
    else res.found.headOption match {
      case Some((_, MatchType.ExactMatch, _)) => Cell.Exact
      case Some((_, MatchType.InOverlap, _))  => Cell.Overlap
      case _                                  => Cell.Miss
    }
  }

  /** Label for change type in each cell (SAME/MINOR/MAJOR/DELETED or "") */
  private def labelOf(res: Result): String = res.classification.element match {
    case ElementClassification.SAME          => "SAME"
    case ElementClassification.MINOR_CHANGE  => "MINOR"
    case ElementClassification.MAJOR_CHANGE  => "MAJOR"
    case ElementClassification.DELETED       => "DELETED"
    case _                                   => ""
  }

  /**
   * For one algorithm + site, build numeric matrix:
   *   key = the v1 (toFind) absolute xpath → Map[date -> cell-code].
   * This keeps element identity stable across versions.
   */
  private def collectSiteMatrix(stats: EvaluationStatistics, site: String)
  : Map[String, Map[LocalDate, Int]] = {
    val siteResults: Seq[Result] =
      stats.results
        .filter(_.site == site)
        .flatMap(_.results)

    val rootOf = makeRootResolver(buildEdges(siteResults))

    siteResults
      .groupBy(r => rootOf(r.toFind.xpath.chrome))
      .view
      .mapValues { rs =>
        rs.map(r => r.date -> encode(r)).toMap
      }
      .toMap
  }

  /**
   * For one algorithm + site, build label matrix:
   *   key = the v1 (toFind) absolute xpath → Map[date -> "SAME"/"MINOR"/"MAJOR"/"DELETED"/""].
   */
  private def collectSiteClassMatrix(stats: EvaluationStatistics, site: String)
  : Map[String, Map[LocalDate, String]] = {
    val siteResults: Seq[Result] =
      stats.results
        .filter(_.site == site)
        .flatMap(_.results)

    val rootOf = makeRootResolver(buildEdges(siteResults))

    siteResults
      .groupBy(r => rootOf(r.toFind.xpath.chrome))
      .view
      .mapValues { rs =>
        rs.map(r => r.date -> labelOf(r)).toMap
      }
      .toMap
  }

  /** Write semicolon-separated CSV (ints): optional first line with xpath meta. */
  private def writeCsvInts(header: Vector[String], rows: Seq[AlgoRow], out: Path, xpathMeta: Option[String]): Unit = {
    if (!Files.exists(out.getParent)) Files.createDirectories(out.getParent)
    val headerLine = header.mkString(";")
    val body = rows.map(r => (r.algorithm +: r.values.map(_.toString)).mkString(";"))
    val meta = xpathMeta.map(xp => s"# XPATH=$xp\n").getOrElse("")
    val csv = (meta + (headerLine +: body).mkString("\n"))
    Files.write(out, csv.getBytes(StandardCharsets.UTF_8))
  }

  /** Write semicolon-separated CSV (strings): optional first line with xpath meta. */
  private def writeCsvStrings(header: Vector[String], rows: Seq[ClassRow], out: Path, xpathMeta: Option[String]): Unit = {
    if (!Files.exists(out.getParent)) Files.createDirectories(out.getParent)
    val headerLine = header.mkString(";")
    val body = rows.map(r => (r.algorithm +: r.values).mkString(";"))
    val meta = xpathMeta.map(xp => s"# XPATH=$xp\n").getOrElse("")
    val csv = (meta + (headerLine +: body).mkString("\n"))
    Files.write(out, csv.getBytes(StandardCharsets.UTF_8))
  }

  /** Sanitize very long/ugly element keys into safe file names; use index fallback to keep unique. */
  private def safeName(key: String, idx: Int): String = {
    val base = key.replaceAll("[^a-zA-Z0-9._-]", "_")
    val trimmed = if (base.length <= 120) base else base.take(120)
    val suffix = f"_$idx%04d"
    trimmed + suffix
  }
}