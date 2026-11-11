package vista

import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import upickle.default._

object VistaCache {
  // Optional read-only mode: sbt -Dvista.cache.readOnly=true run
  private val ReadOnly: Boolean =
    java.lang.Boolean.getBoolean("vista.cache.readOnly")

  private val cacheFile = Paths.get("cache/vista_cache.json")

  /** Keys are "screenshot|template" file paths; they are stored relative to the project ("sites/...") so that the
    * cache works on any machine. Absolute keys (older caches) are normalized when loaded. */
  def normalize(key: String): String =
    key.split('|').map { part =>
      val i = part.indexOf("/sites/")
      if (i >= 0) part.substring(i + 1) else part
    }.mkString("|")

  // Load once, but keep a *mutable* copy we update during the run.
  @volatile private var cacheMap: Map[String, (Int, Int)] = {
    if (Files.exists(cacheFile) && Files.size(cacheFile) > 0) {
      val json = new String(Files.readAllBytes(cacheFile), UTF_8)
      read[Map[String, (Int, Int)]](json).map((k, v) => normalize(k) -> v)
    } else Map.empty
  }

  /** Pure read: returns Some if present, else None. */
  def get(key: String): Option[(Int, Int)] =
    cacheMap.get(normalize(key))

  /** Require present (for tests/tools). */
  def require(key: String): (Int, Int) =
    cacheMap.getOrElse(normalize(key), throw new NoSuchElementException(s"VistaCache miss for key: $key"))

  /** Get or compute and persist (unless read-only). */
  def getOrCompute(key: String)(compute: => (Int, Int)): (Int, Int) = {
    cacheMap.getOrElse(normalize(key), {
      val res = compute
      if (!ReadOnly) {
        // Update the in-memory map first, then save the whole map.
        cacheMap = cacheMap + (normalize(key) -> res)
        save(cacheMap)
      }
      res
    })
  }

  private def save(updated: Map[String, (Int, Int)]): Unit = {
    val json = write(updated, indent = 2)
    Files.createDirectories(cacheFile.getParent)
    Files.write(cacheFile, json.getBytes(UTF_8))
  }
}