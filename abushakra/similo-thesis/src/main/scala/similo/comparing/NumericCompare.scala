package similo.comparing

object NumericCompare {
  /** For COLOR: Normalized similarity 1 - |a-b|/max, clamped to [0,1]. If max<=0, return 0.0 */
  def normalized(max: Double)(a: Int, b: Int): Double =
    if (max <= 0.0) 0.0
    else {
      val d = Math.abs(a - b).toDouble
      val s = 1.0 - d / max
      Math.max(0.0, Math.min(1.0, s))
    }
}