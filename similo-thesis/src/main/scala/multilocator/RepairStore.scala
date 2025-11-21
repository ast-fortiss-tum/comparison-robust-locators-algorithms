package multilocator

import java.nio.file.{Files, Paths, Path, StandardOpenOption}
import java.time.Instant
import scala.util.Try
import ujson._

object RepairKeys {
  /** Stable key for an element from version A (use site, date, page, and the original abs xpath if you have it). */
  def from(element: elements.Element): String = {
    val (date, site) = util.Load.getDateAndSiteFromArchiveUrl(element.url.getOrElse(""))
      .getOrElse(("unknown-date","unknown-site"))
    // chrome absolute (or any stable id you have for the source element)
    val baseAbs = element.xpath.chrome
    s"$site#$date#${baseAbs}"
  }
}

final case class HealedEntry(
                              locators: Seq[GeneratedLocator],
                              confirmations: Int,
                              lastUpdatedEpochSec: Long
                            )

object HealedEntry {

  private def asInt(v: ujson.Value, default: Int = 0): Int = v match {
    case ujson.Num(n) => n.toInt
    case ujson.Str(s) => Try(s.trim.toInt).getOrElse(default)
    case _            => default
  }

  private def asLong(v: ujson.Value, default: Long = 0L): Long = v match {
    case ujson.Num(n) => n.toLong
    case ujson.Str(s) => Try(s.trim.toLong).getOrElse(default)
    case _            => default
  }

  private def asDouble(v: ujson.Value, default: Double = 0.0): Double = v match {
    case ujson.Num(n) => n
    case ujson.Str(s) => Try(s.trim.toDouble).getOrElse(default)
    case _            => default
  }

  private def asString(v: ujson.Value, default: String = ""): String = v match {
    case ujson.Str(s) => s
    case other        => other.toString
  }

  def toJson(e: HealedEntry): ujson.Value = Obj(
    "confirmations" -> e.confirmations,
    "lastUpdated"   -> e.lastUpdatedEpochSec, // keep numeric epoch seconds
    "locators"      -> Arr.from(e.locators.map(gl => Obj(
      "strategy" -> gl.strategy,
      "xpath"    -> gl.xpath,
      "weight"   -> gl.weight
    )))
  )

  /** Tolerant reader: accepts lastUpdated as number or string; same for weight. */
  def fromJson(v: ujson.Value): HealedEntry = {
    val obj  = v.obj
    val conf = obj.get("confirmations").map(asInt(_, 0)).getOrElse(0)
    val ts   = obj.get("lastUpdated").map(asLong(_, 0L)).getOrElse(0L)

    val locsArr = obj.get("locators") match {
      case Some(ujson.Arr(a)) => a.toSeq
      case _                  => Seq.empty
    }

    val locs = locsArr.map { j =>
      val o = j.obj
      val strategy = asString(o.getOrElse("strategy", ujson.Str("")), "")
      val xpath    = asString(o.getOrElse("xpath",    ujson.Str("")), "")
      val weight   = asDouble(o.getOrElse("weight",   ujson.Num(0.0)), 0.0)
      GeneratedLocator(xpath = xpath, strategy = strategy, weight = weight)
    }

    HealedEntry(locs, conf, ts)
  }
}

object RepairStore {
  // Promotion thresholds (tune as you like)
  val MinConfirmations  = 1
  val MaxEntriesPerFile = 5000 // keep the file sane

  private def repairsDir(site: String, date: String): Path =
    // store file directly under the date folder (no "repairs" subdir)
    Paths.get("sites", site, date)

  private def storePath(site: String, date: String): Path =
    repairsDir(site, date).resolve("healed-locators.json")

  private def ensureDir(p: Path): Unit =
    if (!Files.exists(p)) Files.createDirectories(p)

  private def loadAll(site: String, date: String): Map[String, HealedEntry] = {
    val p = storePath(site, date)
    if (!Files.exists(p)) return Map.empty
    val txt = new String(Files.readAllBytes(p), "UTF-8")
    if (txt.trim.isEmpty) return Map.empty
    val root = ujson.read(txt)
    root.obj.view.map { case (k, v) => k -> HealedEntry.fromJson(v) }.toMap
  }

  private def saveAll(site: String, date: String, data: Map[String, HealedEntry]): Unit = {
    ensureDir(repairsDir(site, date))
    val root = Obj.from(
      data.iterator.map { case (k, e) => k -> HealedEntry.toJson(e) }
    )
    val pretty = ujson.write(root, indent = 2)
    Files.write(
      storePath(site, date),
      pretty.getBytes("UTF-8"),
      StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE
    )
  }

  /** Load healed locators for this element *only if* they reached MinConfirmations. */
  def loadPromoted(element: elements.Element): Option[Seq[GeneratedLocator]] = {
    val (date, site) = util.Load.getDateAndSiteFromArchiveUrl(element.url.getOrElse(""))
      .getOrElse(("unknown-date","unknown-site"))
    val key  = RepairKeys.from(element)
    val all  = loadAll(site, date)
    all.get(key).filter(_.confirmations >= MinConfirmations).map(_.locators)
  }

  /** Increment confirmations if same set; otherwise reset to 1 for the new set. */
  def updateConfirm(elementA: elements.Element, fresh: Seq[GeneratedLocator]): Unit = {
    val (date, site) = util.Load.getDateAndSiteFromArchiveUrl(elementA.url.getOrElse(""))
      .getOrElse(("unknown-date","unknown-site"))
    val key  = RepairKeys.from(elementA)
    val now  = Instant.now().getEpochSecond
    val all  = loadAll(site, date)

    def sameLocators(a: Seq[GeneratedLocator], b: Seq[GeneratedLocator]): Boolean = {
      if (a.length != b.length) return false
      a.zip(b).forall { case (x, y) =>
        x.strategy == y.strategy && x.xpath == y.xpath // weight diffs don’t matter for identity
      }
    }

    val updated = all.get(key) match {
      case Some(old) if sameLocators(old.locators, fresh) =>
        all + (key -> old.copy(confirmations = old.confirmations + 1, lastUpdatedEpochSec = now))
      case _ =>
        all + (key -> HealedEntry(fresh, confirmations = 1, lastUpdatedEpochSec = now))
    }

    // optional: trim if the file grows too big
    val trimmed =
      if (updated.size <= MaxEntriesPerFile) updated
      else updated.toSeq.sortBy(-_._2.lastUpdatedEpochSec).take(MaxEntriesPerFile).toMap

    saveAll(site, date, trimmed)
  }
}