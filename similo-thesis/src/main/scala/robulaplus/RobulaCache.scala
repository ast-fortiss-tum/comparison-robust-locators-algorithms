package robulaplus

import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import upickle.default._

object RobulaCache {
  // Turn on read-only via: sbt -Drobula.cache.readOnly=true run
  private val ReadOnly: Boolean =
    java.lang.Boolean.getBoolean("robula.cache.readOnly")

  private val cacheFile = Paths.get("cache/robula_cache.json")

  // Load once at startup (fast path: read whole file, parse into immutable Map)
  private val cacheMap: Map[String, String] = {
    if (Files.exists(cacheFile) && Files.size(cacheFile) > 0) {
      val json = new String(Files.readAllBytes(cacheFile), UTF_8)
      read[Map[String, String]](json)
    } else Map.empty
  }

  /** Pure read: returns Some(value) if present, else None. No disk I/O. */
  def get(key: String): Option[String] =
    cacheMap.get(key)

  /** Read-only require: throws if missing (use when you *expect* it to be cached). */
  def require(key: String): String =
    cacheMap.getOrElse(key, throw new NoSuchElementException(s"RobulaCache miss for key: $key"))

  /** Backward-compatible API: if read-only, NEVER compute/write; otherwise behave like before. */
  def getOrCompute(key: String)(compute: => String): String =
    if (ReadOnly) cacheMap.getOrElse(key,
      throw new NoSuchElementException(s"RobulaCache (read-only) miss for key: $key"))
    else {
      // write-enabled mode: compute and persist
      val result = cacheMap.getOrElse(key, compute)
      if (!cacheMap.contains(key)) save(cacheMap + (key -> result))
      result
    }

  // Only used in write-enabled mode
  private def save(updated: Map[String, String]): Unit = {
    val json = write(updated)
    Files.createDirectories(cacheFile.getParent)
    Files.write(cacheFile, json.getBytes(UTF_8))
  }
}