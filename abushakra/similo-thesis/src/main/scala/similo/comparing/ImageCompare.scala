package similo.comparing

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.io.File

object PerceptualHash {
  /** Average-hash (8x8 grayscale) → 64-bit bitstring in a Long. */
  def aHash64(path: String): Option[Long] = try {
    val img = ImageIO.read(new File(path))
    if (img == null) return None

    val scaled = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_GRAY)
    val g = scaled.createGraphics()
    g.drawImage(img, 0, 0, 8, 8, null)
    g.dispose()

    val pixels = new Array[Int](64)   // <-- FIXED: type + length
    var sum = 0
    var i = 0
    for (y <- 0 until 8; x <- 0 until 8) {
      val p = scaled.getRGB(x, y) & 0xff
      pixels(i) = p
      sum += p
      i += 1
    }
    val avg = sum / 64.0

    var h = 0L
    for (p <- pixels) h = (h << 1) | (if (p >= avg) 1 else 0)
    Some(h)
  } catch { case _: Throwable => None }

  def hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a ^ b)

  /** Similarity in [0,1]; 1 is identical hashes, 0 is max distance. Missing → 0.0. */
  def similarity(p1: Option[Long], p2: Option[Long]): Double =
    (for (a <- p1; b <- p2) yield 1.0 - hamming(a, b) / 64.0).getOrElse(0.0)
}

/** Plug-in comparator for AttributeComparison over Option[Long] pHashes. */
object ImageCompare {
  val imageCompare: ValueCompare[Option[Long]] =
    ValueCompare[Option[Long]]("phash", (l: Option[Long], r: Option[Long]) =>
      PerceptualHash.similarity(l, r)
    )
}