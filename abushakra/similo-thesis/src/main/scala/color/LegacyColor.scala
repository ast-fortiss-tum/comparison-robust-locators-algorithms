package color

import elements.Element
import similo.AttributeComparison
import similo.comparing.{StringCompare, ValueCompare, ImageCompare, PerceptualHash, NumericCompare}
import similo.similo.Similo
import java.nio.file.{Files, Paths}

/**
 * LegacyColor: COLOR-like similarity according to the paper:
 * - Binary equality for tag/size.
 * - Levenshtein for string-like attributes (id, class, name, value, type, alt, src, href, onclick, text, label).
 * - Numeric normalized similarity for x/y/width/height: 1 - |a-b| / Max.
 * - Image similarity via perceptual hash (aHash64) if a cropped template is available. (there are currently only very few missing)
 */
object LegacyColor extends Similo(
      // Binary (exact equality)
      AttributeComparison(_.tag, 0.21, StringCompare.equal),
      AttributeComparison(e => e.attributes.get("size"), 0.26, StringCompare.equal.forOption),
      // Image (perceptual hash; 0 if missing)
      AttributeComparison(e => {
            val path = util.Load.getFilePathToTemplates(e.url.getOrElse(""), e)
            if (path.nonEmpty && Files.exists(Paths.get(path))) PerceptualHash.aHash64(path) else None
      }, 0.12, ImageCompare.imageCompare),
      // Strings (Levenshtein normalized)
      AttributeComparison(_.id, 0.86, StringCompare.levenshtein.forOption),
      AttributeComparison(_.className, 0.35, StringCompare.levenshtein.forOption),
      AttributeComparison(_.name, 0.80, StringCompare.levenshtein.forOption),
      AttributeComparison(_.value, 0.61, StringCompare.levenshtein.forOption),
      AttributeComparison(_.typeName, 0.44, StringCompare.levenshtein.forOption),
      AttributeComparison(_.alt, 0.57, StringCompare.levenshtein.forOption),
      AttributeComparison(e => e.attributes.get("src"), 0.62, StringCompare.levenshtein.forOption),
      AttributeComparison(_.href, 0.62, StringCompare.levenshtein.forOption),
      AttributeComparison(e => e.attributes.get("onclick"), 0.66, StringCompare.levenshtein.forOption),
      AttributeComparison(_.xpath.chrome, 0.72, StringCompare.levenshtein),
      AttributeComparison(_.getVisibleText, 0.76, StringCompare.levenshtein.forOption),
      AttributeComparison(_.neighbourText, 0.31, StringCompare.levenshtein.forOption),
      // Numeric (1 - |a-b| / Max(p)), explicit normalization logic
      AttributeComparison(_.x, 0.72, ValueCompare[Int]("xNorm", (l: Int, r: Int) => {
        val s = 1.0 - (math.abs(l - r).toDouble / LegacyColor.maxX); math.max(0.0, math.min(1.0, s))
      })),
      AttributeComparison(_.y, 0.65, ValueCompare[Int]("yNorm", (l: Int, r: Int) => {
        val s = 1.0 - (math.abs(l - r).toDouble / LegacyColor.maxY); math.max(0.0, math.min(1.0, s))
      })),
      AttributeComparison(_.width, 0.66, ValueCompare[Int]("widthNorm", (l: Int, r: Int) => {
        val s = 1.0 - (math.abs(l - r).toDouble / LegacyColor.maxW); math.max(0.0, math.min(1.0, s))
      })),
      AttributeComparison(_.height, 0.33, ValueCompare[Int]("heightNorm", (l: Int, r: Int) => {
        val s = 1.0 - (math.abs(l - r).toDouble / LegacyColor.maxH); math.max(0.0, math.min(1.0, s))
      }))
) {
      // Adjust accordingly for different screenshot dimensions
      val maxX: Double = 1920.0
      val maxY: Double = 1080.0
      val maxW: Double = 1920.0
      val maxH: Double = 1080.0
}