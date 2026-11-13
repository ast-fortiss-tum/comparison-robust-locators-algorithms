import evalutation.EvaluationData.SiteData
import evalutation.EvaluationStatistics.{BySiteStats, LocatorStats}
import evalutation.{EvaluationComputing, EvaluationStatistics}
import evalutation.CsvExporter
import optimization.GeneticOptimization.{createIntGenotype, fitnessByScore, fitnessByScoreFirstLast, fitnessByTopTen}
import optimization.OneAtTheTime.*
import optimization.{BruteForceOptimization, GeneticOptimization, OneAtTheTime}
import scripts.OverlapUsability.overlapMaxScoreNotTarget
import scripts.{AttributeStability, OverlapUsability}
import similo.baseline.SinglePropertyEqual
import similo.comparing.*
import scala.util.Random
import java.io.File
import util.Load.getDateAndSiteFromArchiveUrl
import scrape.PrettyResults


import similo.comparing.DimensionCompare.*
import similo.comparing.LocationCompare.*
import similo.comparing.MapCompare.*
import similo.comparing.StringCompare.*
import similo.legacy.{LegacySimilo, LegacyVonSimilo, LegacyCombined}
import similo.optimized.{OptimizedCombinedSimilo, OptimizedMinimalSimilo, OptimizedSimilo, OptimizedVonSimilo}
import similo.similo.Similo
import similo.von.VonSimilo
import util.Setup.chrome
import robulaplus.RobulaPlus
import sidereal.Sidereal
import absxpath.absXPath
import idxpath.IDXPath
import montoto.Montoto
import selenium_ide.SeleniumIDE
import vista.Vista
import color.LegacyColor
import multilocator.MultiLocator
import similo.{AttributeComparison, Locator}
import util.Utils
import util.Load
import util.StatsCache
import evalutation.Classification.ElementClassification
import elements.Xpath
import elements.Element
import elements.Element.extract
import elements.Element.getRobulaPlusXPath
//import elements.Dom
import scrape.SetupDrivers
import scrape.LoadAllWebsites
import scrape.HeatmapExportPerElement
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.remote.RemoteWebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.{WebDriver, WebElement, By}
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.firefox.{FirefoxDriver, FirefoxDriverLogLevel, FirefoxOptions, FirefoxProfile}
import java.time.LocalDate
import java.time.Duration
import util.Load.getFilePathToChromeDOM
import java.nio.file.Path

import upickle.default.*
import EvaluationStatistics.given
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets

object Main:

  def main(args: Array[String]): Unit =
    // Smoke test: robustness of the locators that only need elements.json
    // (same computation as printRobustnessTable). The other locators need chromeDOM.html / templates.
    java.util.Locale.setDefault(java.util.Locale.US)
    println("Algorithm,AccuracyAll%,AccAll_Num,AccAll_Denom,AccuracyMinor%,AccMinor_Num,AccMinor_Denom,AccuracyMajor%,AccMajor_Num,AccMajor_Denom,Fitness,Fitness%,LocatorBreakSuccess%,LocatorBreak_Num,LocatorBreak_Denom,Seconds")
    // All algorithms on the sites given as arguments (default: the 3 pilot sites with regenerated chromeDOM.html),
    // per site and merged ("ALL"). DOM-based locators are wrapped in Guarded: an element whose locator cannot be
    // generated (e.g. not resolvable in the DOM) scores 0 instead of aborting the run.
    val sites = if args.nonEmpty then args.toSeq else Seq("wikipedia", "zillow", "amazon")
    val algorithms: Seq[(String, similo.Locator)] = Seq(
      "LegacySimilo" -> LegacySimilo,
      "OptimizedSimilo" -> OptimizedSimilo,
      "VonSimilo" -> LegacyVonSimilo,
      "OptimizedVonSimilo" -> OptimizedVonSimilo,
      "LegacyCombined" -> LegacyCombined,
      "OptimizedCombined" -> OptimizedCombinedSimilo,
      "OptimizedMinimal" -> OptimizedMinimalSimilo,
      "COLOR" -> LegacyColor,
      "VISTA" -> Vista(),
      "absXPath" -> similo.Guarded(absXPath()),
      "IDXPath" -> similo.Guarded(IDXPath()),
      "Montoto" -> similo.Guarded(Montoto()),
      "SeleniumIDE" -> similo.Guarded(SeleniumIDE()),
      "RobulaPlus" -> similo.Guarded(RobulaPlus()),
      "EAGL" -> similo.Guarded(eagl.Eagl()),
      "EAGL-scaled-adobe" -> similo.Guarded(eagl.Eagl("cache/eagl_scaled_adobe_cache.json")), // released model, paper features
      "EAGL-scaled-f0" -> similo.Guarded(eagl.Eagl("cache/eagl_scaled_f0_cache.json")), // paper setting, rq4 category model fold 0
      "EAGL-scaled-f1" -> similo.Guarded(eagl.Eagl("cache/eagl_scaled_f1_cache.json")), // paper setting, rq4 category model fold 1
      "EAGL-scaled-f2" -> similo.Guarded(eagl.Eagl("cache/eagl_scaled_f2_cache.json")), // paper setting, rq4 category model fold 2
      "EAGL-scaled-f3" -> similo.Guarded(eagl.Eagl("cache/eagl_scaled_f3_cache.json")), // paper setting, rq4 category model fold 3
      "EAGL-scaled-f4" -> similo.Guarded(eagl.Eagl("cache/eagl_scaled_f4_cache.json")), // paper setting, rq4 category model fold 4
      "SIDEREAL" -> similo.Guarded(siderealnew.SiderealNew()),
      "SIDEREAL-uniform" -> similo.Guarded(siderealnew.SiderealNew(useHistory = false)),
      "MultiLocator" -> similo.Guarded(multilocator.MultiLocator(false, true)), // VISTA fallback when no voter resolves
      "MultiLocator-noVISTA" -> similo.Guarded(multilocator.MultiLocator(false, false)),
      "MultiLocator+repair" -> similo.Guarded(multilocator.MultiLocator(true, true))
    )
    def row(name: String, scope: String, stats: EvaluationStatistics, seconds: Double): String =
      val (accAll, accAllNum, accAllDenom, accMinor, accMinorNum, accMinorDenom,
           accMajor, accMajorNum, accMajorDenom, fit, fitPercent, breakSuccess, breakNum, breakDenom) = stats.robustnessStats
      f"$name@$scope,$accAll%.2f,$accAllNum%d,$accAllDenom%d,$accMinor%.2f,$accMinorNum%d,$accMinorDenom%d,$accMajor%.2f,$accMajorNum%d,$accMajorDenom%d,$fit%.2f,$fitPercent%.2f,$breakSuccess%.2f,$breakNum%d,$breakDenom%d,$seconds%.1f"
    // Optional subset, e.g. ALGOS=absXPath,IDXPath (comma-separated names as above)
    val only = sys.env.get("ALGOS").map(_.split(",").map(_.trim).toSet)
    for (name, locator) <- algorithms if only.forall(_.contains(name)) do
      val t0 = System.nanoTime()
      val perSite = sites.map { s =>
        val t = System.nanoTime()
        val stats = EvaluationComputing.computeAll(locator, Seq(java.nio.file.Path.of("sites", s)))
        println(row(name, s, stats, (System.nanoTime() - t) / 1e9))
        stats
      }
      println(row(name, "ALL", perSite.reduce(_ append _), (System.nanoTime() - t0) / 1e9))
      locator match
        case g: similo.Guarded if g.failedInits > 0 => println(s"# $name: no locator generated for ${g.failedInits} elements")
        case _ => ()

//    printRobustnessTable()



//    val sites = Seq(
//      "accuweather","adobe","amazon","apple","bestbuy","bing","discord","ebay","espn","etsy",
//      "facebook","fidelity","google","indeed","linkedin","netflix","office","paypal","reddit","roblox",
//      "t-mobile","tiktok","twitter","usps","walmart","weather","wikipedia","yahoo","zillow","zoom"
//    )
//    val locator: Locator = Vista()
//    for (siteName <- sites) {
//      val basePath = "/Users/bashirabushakra/Desktop/BachelorThesis/CodeBase/web-element-localization-using-similo-like-approaches/similo-thesis/sites/" + siteName
//      val elementsA = Load.getDateElementsFor(basePath + "/2018-09-01")
//      val elements10 = Load.getFirst10ElementsFromSite(basePath)
//      val elementsB = Load.getDateElementsFor(basePath + "/2019-01-01")
//      for (x <- elements10) {
//        val state = locator.init(x, elementsA)
//        val results = locator.locate(state, elementsB)
//        val (site, date) = getDateAndSiteFromArchiveUrl(x.url.getOrElse("")).getOrElse(("", ""))
//        println(s"site: $site, date: $date, Element: ${x.xpath.chrome}")
//      }
//    }


//     println("[RUN] Evaluating MultiLocator with repair ...")
//     val multiRepairStats = EvaluationComputing.computeAll(multilocator.MultiLocator(true, true), mapper = EvaluationComputing.firstToAllMapper)
//     println("[DONE] MultiLocator with repair finished. Calculating next algorithm ...")
//
//     println("[RUN] Evaluating AbsoluteXPath ...")
//     val absXPathStats = EvaluationComputing.computeAll(absxpath.absXPath(), mapper = EvaluationComputing.firstToAllMapper)
//     println("[DONE] AbsoluteXPath finished. Calculating next algorithm ...")
//
//     println("[RUN] Evaluating Montoto ...")
//     val montotoStats = EvaluationComputing.computeAll(montoto.Montoto(), mapper = EvaluationComputing.firstToAllMapper)
//     println("[DONE] Montoto finished. Calculating next algorithm ...")
//
//     println("[RUN] Evaluating SeleniumIDE ...")
//     val seleniumIDEStats = EvaluationComputing.computeAll(selenium_ide.SeleniumIDE(), mapper = EvaluationComputing.firstToAllMapper)
//     println("[DONE] SeleniumIDE finished. Calculating next algorithm ...")
//
//     println("[RUN] Evaluating IDXPath ...")
//     val idxPathStats = EvaluationComputing.computeAll(idxpath.IDXPath(), mapper = EvaluationComputing.firstToAllMapper)
//     println("[DONE] IDXPath finished. Calculating next algorithm ...")
//
//     println("[RUN] Evaluating LegacySimilo ...")
//     val similoStats = EvaluationComputing.computeAll(LegacySimilo, mapper = EvaluationComputing.firstToAllMapper)
//     println("[DONE] LegacySimilo finished. Calculating next algorithm ...")
//
//     println("[RUN] Evaluating OptimizedSimilo ...")
//     val optimizedSimiloStats = EvaluationComputing.computeAll(OptimizedSimilo, mapper = EvaluationComputing.firstToAllMapper)
//     println("[DONE] OptimizedSimilo finished. Calculating next algorithm ...")
//
//     println("[RUN] Evaluating COLOR ...")
//     val colorStats = EvaluationComputing.computeAll(color.LegacyColor, mapper = EvaluationComputing.firstToAllMapper)
//     println("[DONE] COLOR finished. Calculating next algorithm ...")
//
//     println("[RUN] Evaluating ROBULA+ ...")
//     val robulaPlusStats = EvaluationComputing.computeAll(robulaplus.RobulaPlus(), mapper = EvaluationComputing.firstToAllMapper)
//     println("[DONE] ROBULA+ finished. Calculating next algorithm ...")
//
//     println("[RUN] Evaluating VISTA ...")
//     val vistaStats = EvaluationComputing.computeAll(vista.Vista(), mapper = EvaluationComputing.firstToAllMapper)
//     println("[DONE] VISTA finished. Calculating next algorithm ...")
//
//     println("[RUN] Evaluating MultiLocator ...")
//     val multiStats = EvaluationComputing.computeAll(multilocator.MultiLocator(false, true), mapper = EvaluationComputing.firstToAllMapper)
//     println("[DONE] MultiLocator finished. Calculating next algorithm ...")
//
//     println("[RUN] Evaluating VON Similo ...")
//     val VonSimiloStats = EvaluationComputing.computeAll(LegacyVonSimilo, mapper = EvaluationComputing.firstToAllMapper)
//     println("[DONE] VON Similo finished. Calculating next steps ...")
//     CsvExporter.exportAll("LegacySimilo", similoStats, Paths.get("results/survivability_results/LegacySimilo"))
//     CsvExporter.exportAll("OptimizedSimilo", optimizedSimiloStats, Paths.get("results/survivability_results/OptimizedSimilo"))
//     CsvExporter.exportAll("VonSimilo", VonSimiloStats, Paths.get("results/survivability_results/VonSimilo"))
//     CsvExporter.exportAll("Vista", vistaStats, Paths.get("results/survivability_results/Vista"))
//     CsvExporter.exportAll("Multi Locator", multiStats, Paths.get("results/survivability_results/Multi-Locator (VISTA)"))
//     CsvExporter.exportAll("Multi Locator + repair", multiRepairStats, Paths.get("results/survivability_results/Multi-Locator Repair (VISTA)"))
//     CsvExporter.exportAll("Robula+", robulaPlusStats, Paths.get("results/survivability_results/RobulaPlus"))
//     CsvExporter.exportAll("Color", colorStats, Paths.get("results/survivability_results/Color"))
//     CsvExporter.exportAll("absXPath", absXPathStats, Paths.get("results/survivability_results/absXPath"))
//     CsvExporter.exportAll("Montoto", montotoStats, Paths.get("results/survivability_results/Montoto"))
//     CsvExporter.exportAll("IDXPath", idxPathStats, Paths.get("results/survivability_results/IDXPath"))
//     CsvExporter.exportAll("SeleniumIDE", seleniumIDEStats, Paths.get("results/survivability_results/SeleniumIDE"))
//
//     val all = Seq(
//            "LegacySimilo"           -> similoStats,
//            "OptimizedSimilo" -> optimizedSimiloStats,
//            "COLOR"          -> colorStats,
//            "VONSimilo" -> VonSimiloStats,
//            "Multi-Locator v"  -> multiStats,
//            "Multi-Locator v w/repair" -> multiRepairStats,
//            "ROBULA+"        -> robulaPlusStats,
//            "SeleniumIDE"        -> seleniumIDEStats,
//            "Montoto"        -> montotoStats,
//            "IDXPath"        -> idxPathStats,
//            "absXPath"        -> absXPathStats,
//            "VISTA"          -> vistaStats
//)

// This writes per-element CSVs for each site:
// After that run this: python3 results/survivability_heatmaps/plot_heatmaps.py results/survivability_heatmaps
//python3 results/survivability_results/figures/plot_survival_ratios_combined.py
//     HeatmapExportPerElement.save(all, Paths.get("results/survivability_heatmaps"))


  //    val lines = BySiteStats.header +: stats.extensiveStatsBySite.map(_.csv)
//    Files.write(Paths.get("results/robulaplus/stats.csv"), lines.mkString("\n").getBytes(StandardCharsets.UTF_8))


  private def bruteForceEliminateProperties(): Unit =
    BruteForceOptimization(
      (values: Seq[Double]) =>
        Similo(
          AttributeComparison(_.tag, values.head, StringCompare.jaccard),
          AttributeComparison(_.className, values(1), StringCompare.equal.forOption),
          AttributeComparison(_.name, values(2), StringCompare.levenshtein.forOption),
          AttributeComparison(_.id, values(3), StringCompare.levenshtein.forOption),
          AttributeComparison(_.href, values(4), StringCompare.equal.forOption),
          AttributeComparison(_.alt, values(5), StringCompare.equal.forOption),
          AttributeComparison(_.typeName, values(6), StringCompare.equal.forOption),
          AttributeComparison(_.ariaLabel, values(7), StringCompare.jaccard.forOption),
          AttributeComparison(_.xpath.chrome, values(8), StringCompare.jaccard),
          AttributeComparison(_.xpath.idxpath, values(9), StringCompare.levenshtein),
          AttributeComparison(_.isButton, values(10), ValueCompare.equals),
          AttributeComparison(_.location, values(11), LocationCompare.distanceMediumDecay),
          AttributeComparison(_.dimension, values(12), DimensionCompare.area),
          AttributeComparison(_.getVisibleText, values(13), StringCompare.levenshtein.forOption),
          AttributeComparison(_.neighbourText, values(14), StringCompare.stringSet.forOption),
          AttributeComparison(_.attributes, values(15), MapCompare.intersectValueCompare)
        ),
      Seq(
        Seq(0.8, 0.0),
        Seq(0.0),
        Seq(2.85),
        Seq(0.5, 0.0),
        Seq(0.95, 0.0),
        Seq(1.85),
        Seq(2.75),
        Seq(0.9, 0.0),
        Seq(0.1, 0.0),
        Seq(0.45, 0.0),
        Seq(0.1, 0.0),
        Seq(1.2),
        Seq(0.35, 0.0),
        Seq(2.8),
        Seq(1.45),
        Seq(1.8)
      ), 0.0
    ).compute()

  private def vonSimiloGeneticOptimizeTopTenGenetic(): Unit =
    val inputWithComparisonOptResults = (values: Seq[Double]) =>
      VonSimilo(
        Seq(
          AttributeComparison(_.tag, values.head, StringCompare.jaroWinkler),
          AttributeComparison(_.className, values(1), StringCompare.equal.forOption),
          AttributeComparison(_.name, values(2), StringCompare.equal.forOption),
          AttributeComparison(_.id, values(3), StringCompare.equal.forOption),
          AttributeComparison(_.href, values(4), StringCompare.equal.forOption),
          AttributeComparison(_.alt, values(5), StringCompare.equal.forOption),
          AttributeComparison(_.typeName, values(6), StringCompare.equal.forOption),
          AttributeComparison(_.ariaLabel, values(7), StringCompare.jaccard.forOption),
          AttributeComparison(_.xpath.chrome, values(8), StringCompare.equal),
          AttributeComparison(_.xpath.idxpath, values(9), StringCompare.levenshtein),
          AttributeComparison(_.isButton, values(10), ValueCompare.equals),
          AttributeComparison(_.location, values(11), LocationCompare.distanceLinear),
          AttributeComparison(_.dimension, values(12), DimensionCompare.area),
          AttributeComparison(_.getVisibleText, values(13), StringCompare.levenshtein.forOption),
          AttributeComparison(_.neighbourText, values(14), StringCompare.stringSet.forOption),
          AttributeComparison(_.attributes, values(15), MapCompare.intersectValueCompare)
        )
      )
    GeneticOptimization(
      inputWithComparisonOptResults,
      fitnessByTopTen,
      createIntGenotype,
      outfile = "results/weight_optimization/von_max_top_ten_four_months.csv"
    ).compute()

  private def basicSimiloGeneticOptimizeScoreFourMonths(): Unit =
    val inputWithComparisonOptResults = (values: Seq[Double]) =>
      Similo(
        AttributeComparison(_.tag, values.head, StringCompare.jaccard),
        AttributeComparison(_.className, values(1), StringCompare.equal.forOption),
        AttributeComparison(_.name, values(2), StringCompare.levenshtein.forOption),
        AttributeComparison(_.id, values(3), StringCompare.levenshtein.forOption),
        AttributeComparison(_.href, values(4), StringCompare.equal.forOption),
        AttributeComparison(_.alt, values(5), StringCompare.equal.forOption),
        AttributeComparison(_.typeName, values(6), StringCompare.equal.forOption),
        AttributeComparison(_.ariaLabel, values(7), StringCompare.jaccard.forOption),
        AttributeComparison(_.xpath.chrome, values(8), StringCompare.jaccard),
        AttributeComparison(_.xpath.idxpath, values(9), StringCompare.levenshtein),
        AttributeComparison(_.isButton, values(10), ValueCompare.equals),
        AttributeComparison(_.location, values(11), LocationCompare.distanceMediumDecay),
        AttributeComparison(_.dimension, values(12), DimensionCompare.area),
        AttributeComparison(_.getVisibleText, values(13), StringCompare.levenshtein.forOption),
        AttributeComparison(_.neighbourText, values(14), StringCompare.stringSet.forOption),
        AttributeComparison(_.attributes, values(15), MapCompare.intersectValueCompare)
      )
    GeneticOptimization(
      inputWithComparisonOptResults,
      fitnessByScore,
      createIntGenotype,
      outfile = "results/weight_optimization/similo_max_score_four_month.csv"
    ).compute()

  private def basicSimiloGeneticOptimizeScoreFourMonthsWithEliminated(): Unit =
    val inputWithComparisonOptResults = (values: Seq[Double]) =>
      Similo(
        AttributeComparison(_.tag, values.head, StringCompare.jaccard),
        AttributeComparison(_.name, values(1), StringCompare.levenshtein.forOption),
        AttributeComparison(_.href, values(2), StringCompare.equal.forOption),
        AttributeComparison(_.alt, values(3), StringCompare.equal.forOption),
        AttributeComparison(_.typeName, values(4), StringCompare.equal.forOption),
        AttributeComparison(_.ariaLabel, values(5), StringCompare.jaccard.forOption),
        AttributeComparison(_.xpath.idxpath, values(6), StringCompare.levenshtein),
        AttributeComparison(_.location, values(7), LocationCompare.distanceMediumDecay),
        AttributeComparison(_.dimension, values(8), DimensionCompare.area),
        AttributeComparison(_.getVisibleText, values(9), StringCompare.levenshtein.forOption),
        AttributeComparison(_.neighbourText, values(10), StringCompare.stringSet.forOption),
        AttributeComparison(_.attributes, values(11), MapCompare.intersectValueCompare)
      )
    GeneticOptimization(
      inputWithComparisonOptResults,
      fitnessByScore,
      createIntGenotype,
      outfile = "results/weight_optimization/similo_max_score_eliminated_four_month.csv",
      props = 12
    ).compute()

  private def basicSimiloGeneticOptimizeScoreFiveYears(): Unit =
    val inputWithComparisonOptResults = (values: Seq[Double]) =>
      Similo(
        AttributeComparison(_.tag, values.head, StringCompare.jaroWinkler),
        AttributeComparison(_.className, values(1), StringCompare.equal.forOption),
        AttributeComparison(_.name, values(2), StringCompare.equal.forOption),
        AttributeComparison(_.id, values(3), StringCompare.jaccard.forOption),
        AttributeComparison(_.href, values(4), StringCompare.equal.forOption),
        AttributeComparison(_.alt, values(5), StringCompare.equal.forOption),
        AttributeComparison(_.typeName, values(6), StringCompare.equal.forOption),
        AttributeComparison(_.ariaLabel, values(7), StringCompare.jaccard.forOption),
        AttributeComparison(_.xpath.chrome, values(8), StringCompare.equal),
        AttributeComparison(_.xpath.idxpath, values(9), StringCompare.levenshtein),
        AttributeComparison(_.isButton, values(10), ValueCompare.equals),
        AttributeComparison(_.location, values(11), LocationCompare.distanceLinear),
        AttributeComparison(_.dimension, values(12), DimensionCompare.aspectRatio),
        AttributeComparison(_.getVisibleText, values(13), StringCompare.levenshtein.forOption),
        AttributeComparison(_.neighbourText, values(14), StringCompare.stringSet.forOption),
        AttributeComparison(_.attributes, values(15), MapCompare.intersectValueCompare)
      )
    GeneticOptimization(
      inputWithComparisonOptResults,
      fitnessByScoreFirstLast,
      createIntGenotype,
      outfile = "results/weight_optimization/similo_max_score_five_year.csv"
    ).compute()

  private def calculateExtensiveStats(): Unit =
    val stats = EvaluationComputing.computeAll(OptimizedSimilo).extensiveStatsBySite
    println(BySiteStats.header)
    stats.foreach: s =>
      println(s.csv)

  private def basicSimiloOatFourMonths(): Unit =
    basicSimiloOat("results/comparison_optimization/similo_oat_four_month_fitness.csv")

  private def basicSimiloOatFiveYears(): Unit =
    basicSimiloOat(
      "results/comparison_optimization/similo_oat_five_year_fitness.csv",
      mapper = EvaluationComputing.firstAndLastMapper
    )

  private def basicSimiloOat(writeTo: String, mapper: SiteData => SiteData = identity): Unit =
    OneAtTheTime(
      Seq(
        AttributeComparisons("tag", _.tag, equal, stringComparisons, 1.5),
        AttributeComparisons("className", _.className, levenshtein.forOption, stringOptionComparisons, 0.5),
        AttributeComparisons("name", _.name, equal.forOption, stringOptionComparisons, 1.5),
        AttributeComparisons("id", _.id, equal.forOption, stringOptionComparisons, 1.5),
        AttributeComparisons("href", _.href, levenshtein.forOption, stringOptionComparisons, 0.5),
        AttributeComparisons("alt", _.alt, levenshtein.forOption, stringOptionComparisons, 0.5),
        AttributeComparisons("xpath.chrome", _.xpath.chrome, levenshtein, stringComparisons, 0.5),
        AttributeComparisons("xpath.idxpath", _.xpath.idxpath, levenshtein, stringComparisons, 0.5),
        AttributeComparisons("location", _.location, distanceLinear, locationComparisons, 0.5),
        AttributeComparisons("dimension", _.dimension, area, dimensionComparisons, 0.5),
        AttributeComparisons("visible_text", _.getVisibleText, levenshtein.forOption, stringOptionComparisons, 1.5),
        AttributeComparisons("neighbour_text", _.neighbourText, stringSet.forOption, stringOptionComparisons, 1.5),
        AttributeComparisons("type", _.typeName, equal.forOption, stringOptionComparisons),
        AttributeComparisons("aria-label", _.ariaLabel, levenshtein.forOption, stringOptionComparisons),
        AttributeComparisons("attributes", _.attributes, intersectValueCompare, mapComparisons)
      ),
      input =>
        Similo(
          Seq(
            AttributeComparison(_.isButton, 0.5, ValueCompare.equals)
          ) ++ input*
        ),
      writeTo = writeTo,
      mapper = mapper
    ).sequentialRandom(50)

  private def basicSimiloBruteForce4Months(): Unit =
    OneAtTheTime(
      Seq(
        AttributeComparisons("tag", _.tag, jaccard, Seq(jaccard, levenshtein, equal), 1.5), // jaccard
        AttributeComparisons(
          "className",
          _.className,
          levenshtein.forOption,
          Seq(levenshtein.forOption, equal.forOption),
          0.5
        ), // equal
        AttributeComparisons(
          "name",
          _.name,
          levenshtein.forOption,
          Seq(equal.forOption, levenshtein.forOption),
          1.5
        ), // levenshtein
        AttributeComparisons(
          "alt",
          _.alt,
          levenshtein.forOption,
          Seq(levenshtein.forOption, equal.forOption),
          0.5
        ), // equal
        AttributeComparisons("xpath.chrome", _.xpath.chrome, levenshtein, Seq(levenshtein, jaccard), 0.5), // jaccard
        AttributeComparisons(
          "aria-label",
          _.ariaLabel,
          levenshtein.forOption,
          Seq(jaccard.forOption, levenshtein.forOption)
        ) // jaccard
      ),
      input =>
        Similo(
          Seq(
            AttributeComparison(_.id, 1.5, levenshtein.forOption),
            AttributeComparison(_.href, 0.5, equal.forOption),
            AttributeComparison(_.xpath.idxpath, 0.5, levenshtein),
            AttributeComparison(_.isButton, 0.5, ValueCompare.equals),
            AttributeComparison(_.location, 0.5, distanceMediumDecay),
            AttributeComparison(_.dimension, 0.5, area),
            AttributeComparison(_.getVisibleText, 1.5, levenshtein.forOption),
            AttributeComparison(_.neighbourText, 1.5, stringSet.forOption),
            AttributeComparison(_.typeName, 1.0, equal.forOption),
            AttributeComparison(_.attributes, 1.0, intersectValueCompare)
          ) ++ input*
        ),
      writeTo = "results/comparison_optimization/brute_force_four_month_fitness.csv"
    ).bruteForce()

  private def basicSimiloBruteForce5Years(): Unit =
    OneAtTheTime(
      Seq(
        AttributeComparisons(
          "className",
          _.className,
          stringSet.forOption,
          Seq(stringSet.forOption, equal.forOption),
          0.5
        ), // equal
        AttributeComparisons("id", _.id, equal.forOption, Seq(equal.forOption, jaccard.forOption), 1.5), // jaccard
        AttributeComparisons(
          "alt",
          _.alt,
          levenshtein.forOption,
          Seq(levenshtein.forOption, equal.forOption),
          0.5
        ), // equal
        AttributeComparisons("xpath.idxpath", _.xpath.idxpath, equal, Seq(levenshtein, equal), 0.5), // equal
        AttributeComparisons(
          "aria-label",
          _.ariaLabel,
          levenshtein.forOption,
          Seq(jaccard.forOption, levenshtein.forOption)
        ) // jaccard
      ),
      input =>
        Similo(
          Seq(
            AttributeComparison(_.tag, 1.5, jaroWinkler),
            AttributeComparison(_.name, 1.5, equal.forOption),
            AttributeComparison(_.href, 0.5, equal.forOption),
            AttributeComparison(_.xpath.chrome, 0.5, equal),
            AttributeComparison(_.isButton, 0.5, ValueCompare.equals),
            AttributeComparison(_.location, 0.5, distanceLinear),
            AttributeComparison(_.dimension, 0.5, aspectRatio),
            AttributeComparison(_.getVisibleText, 1.5, levenshtein.forOption),
            AttributeComparison(_.neighbourText, 1.5, stringSet.forOption),
            AttributeComparison(_.typeName, 1.0, equal.forOption),
            AttributeComparison(_.attributes, 1.0, intersectValueCompare)
          ) ++ input*
        ),
      writeTo = "results/comparison_optimization/brute_force_five_year_fitness.csv",
      mapper = EvaluationComputing.firstAndLastMapper
    ).bruteForce()

  private def printHeapSizes(): Unit =
    val mb = 1024 * 1024
    val runtime = Runtime.getRuntime
    println("** Used Memory:  " + (runtime.totalMemory - runtime.freeMemory) / mb)
    println("** Free Memory:  " + runtime.freeMemory / mb)
    println("** Max Memory:   " + runtime.maxMemory / mb)

  private def printWholeStats(): Unit =
    val locators = Seq(
      (OptimizedCombinedSimilo, "Optimized Combined"),
      (OptimizedSimilo, "Optimized Similo"),
      (OptimizedMinimalSimilo, "Optimized Minimal"),
      (OptimizedVonSimilo, "Optimized VON Similo"),
      (LegacySimilo, "Legacy Similo"),
      (LegacyVonSimilo, "Legacy VON Similo"),
      (SinglePropertyEqual.tagText, "Tag + Text"),
      (SinglePropertyEqual.idXpath, "ID-XPath"),
      (SinglePropertyEqual.xpath, "XPath"),
      (SinglePropertyEqual.id, "ID"),
    )

    println("Locator,Accuracy,Fitness,Locator Break Found,Found Overlap,Top Ten")
    val printStats: (LocatorStats, String) => Unit = (s, n) =>
      println(s"${n},${s.accuracy},${s.fitness} (${s.fitnessPercent})," +
        s"${s.locatorBreakFound},${s.foundOverlap},${s.topTen}")

    val calculateStats: Seq[Path] => Unit = paths =>
      for locator <- locators do
        val fmStats = EvaluationComputing
          .computeAll(locator._1, paths = paths).extensivePerformanceStats
        printStats(fmStats, locator._2)
      for locator <- locators do
        val fyStats = EvaluationComputing
          .computeAll(locator._1, paths = paths, mapper = EvaluationComputing.firstAndLastMapper)
          .extensivePerformanceStats
        printStats(fyStats, locator._2)

    calculateStats(Load.getFullLoadedSuitableSites)
    // println("\n\n\n")
    calculateStats(Load.crossValidate)

  private def printRobustnessTable(): Unit =
    val locators = Seq(
      ("LegacySimilo", LegacySimilo),
      ("OptimizedSimilo", OptimizedSimilo),
      ("VonSimilo", LegacyVonSimilo),
      ("Vista", Vista()),
      ("RobulaPlus", RobulaPlus()),
//      ("Multi Locator", MultiLocator()),
      ("COLOR", LegacyColor),
      ("SeleniumIDE", SeleniumIDE()),
      ("Montoto", Montoto()),
      ("IDXPath", IDXPath())
//      ("absXPath", absXPath())
    )

    println("Algorithm,AccuracyAll%,AccAll_Num,AccAll_Denom,AccuracyMinor%,AccMinor_Num,AccMinor_Denom,AccuracyMajor%,AccMajor_Num,AccMajor_Denom,Fitness,Fitness%,LocatorBreakSuccess%,LocatorBreak_Num,LocatorBreak_Denom")
    for (name, locator) <- locators do
      val stats = EvaluationComputing.computeAll(locator)
      val (
        accAll, accAllNum, accAllDenom,
        accMinor, accMinorNum, accMinorDenom,
        accMajor, accMajorNum, accMajorDenom,
        fit, fitPercent,
        breakSuccess, breakNum, breakDenom
      ) = stats.robustnessStats
      // robustnessStats returns 14 values: (Double, Int, Int, Double, Int, Int, Double, Int, Int, Double, Double, Double, Int, Int)
      println(f"$name,$accAll%.2f,$accAllNum%d,$accAllDenom%d,$accMinor%.2f,$accMinorNum%d,$accMinorDenom%d,$accMajor%.2f,$accMajorNum%d,$accMajorDenom%d,$fit%.2f,$fitPercent%.2f,$breakSuccess%.2f,$breakNum%d,$breakDenom%d")

//  def benchmarkRunTime(algo: Locator, allElements: Seq[Element]): Unit =
//    println("Number of elements: " + allElements.length)
//    val shuffled = scala.util.Random.shuffle(allElements)
//      for (element <- shuffled) {
//        algo.init(element, allElements)
//      }

// (1) sbt "runMain evalutation.BoxplotStats"
// (2) python3 results/survivability_results/figures/plot_boxplot_from_stats.py
// (3) survival curve: python3 results/survivability_results/figures/plot_survival_curve.py
// (4) for clearing healed-locators cache: python3 sites/clear_healed_files.py