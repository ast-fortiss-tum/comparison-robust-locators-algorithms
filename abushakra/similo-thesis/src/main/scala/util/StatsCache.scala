package util

import java.io._
import java.nio.file.{Files, Paths}
import scala.util.Using

object StatsCache {
  private def ensureParent(path: String): Unit = {
    val p = Paths.get(path).toAbsolutePath
    val parent = p.getParent
    if (parent != null && !Files.exists(parent)) Files.createDirectories(parent)
  }

  def save(path: String, stats: AnyRef): Unit = {
    ensureParent(path)
    Using.resource(new ObjectOutputStream(new FileOutputStream(path))) { oos =>
      oos.writeObject(stats)
      oos.flush()
    }
  }

  def load[T](path: String): Option[T] = {
    val f = new File(path)
    if (!f.exists()) None
    else {
      // Using(...) returns Try[T] → .toOption is valid
      Using(new ObjectInputStream(new FileInputStream(f))) { ois =>
        ois.readObject().asInstanceOf[T]
      }.toOption
    }
  }
}