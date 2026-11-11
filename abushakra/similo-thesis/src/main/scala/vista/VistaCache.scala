package vista

import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import upickle.default._

object VistaCache {
  // Optional read-only mode: sbt -Dvista.cache.readOnly=true run
  private val ReadOnly: Boolean =
    java.lang.Boolean.getBoolean("vista.cache.readOnly")

  private val cacheFile = Paths.get("cache/vista_cache.json")

  // Load once, but keep a *mutable* copy we update during the run.
  @volatile private var cacheMap: Map[String, (Int, Int)] = {
    if (Files.exists(cacheFile) && Files.size(cacheFile) > 0) {
      val json = new String(Files.readAllBytes(cacheFile), UTF_8)
      read[Map[String, (Int, Int)]](json)
    } else Map.empty
  }

  /** Pure read: returns Some if present, else None. */
  def get(key: String): Option[(Int, Int)] =
    cacheMap.get(key)

  /** Require present (for tests/tools). */
  def require(key: String): (Int, Int) =
    cacheMap.getOrElse(key, throw new NoSuchElementException(s"VistaCache miss for key: $key"))

  /** Get or compute and persist (unless read-only). */
  def getOrCompute(key: String)(compute: => (Int, Int)): (Int, Int) = {
    cacheMap.getOrElse(key, {
      val res = compute
      if (!ReadOnly) {
        // Update the in-memory map first, then save the whole map.
        cacheMap = cacheMap + (key -> res)
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