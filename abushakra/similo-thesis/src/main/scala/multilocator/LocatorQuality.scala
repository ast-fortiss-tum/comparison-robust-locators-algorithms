package multilocator

import evalutation.EvaluationData.getSiteData
import util.Load.getJDOMDocumentFromURL
import util.Load.getJDOMElementsByXPath

import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.nio.charset.StandardCharsets

/** Authoring-time quality of the locators the generators produce on the ORIGINAL page (no new version involved):
  * does the XPath resolve to exactly the target element (unique and correct), how long is it, how many positional
  * indices ([3]) and attribute / text predicates does it use. Positional indices are the classic fragility marker.
  * usage: runMain multilocator.LocatorQuality <out csv> <site...>   (first version of each site) */
object LocatorQuality:

  def main(args: Array[String]): Unit =
    val out = Paths.get(args(0))
    val sb = new StringBuilder("site,element,strategy,resolves,unique_and_correct,length,steps,positional,attr_predicates,text_predicates\n")
    val ml = MultiLocator(false, false)
    for s <- args.drop(1) do
      try
        val data = getSiteData(Paths.get("sites", s))
        val first = data.data.head
        for pair <- first.location if !pair.oldElement.isEmpty do
          val el = pair.oldElement
          try
            val doc = getJDOMDocumentFromURL(el.url.getOrElse(""))
            val target = getJDOMElementsByXPath(el.xpath.idxpath, doc).head
            val st = ml.init(el, first.oldElements)
            for l <- List(st.firePathAbs, st.firePathRel, st.seleniumIDE, st.montoto, st.robulaplus) do
              val hits = scala.util.Try(getJDOMElementsByXPath(l.xpath, doc)).getOrElse(Seq.empty)
              val ok = hits.size == 1 && (hits.head eq target)
              val x = l.xpath
              val steps = x.split("/").count(_.nonEmpty)
              val pos = "\\[\\d+\\]".r.findAllIn(x).size
              val attr = "@[a-zA-Z_:-]+".r.findAllIn(x).size
              val txt = "text\\(\\)|contains\\(|normalize-space".r.findAllIn(x).size
              sb.append(s"$s,${el.xpath.chrome.replace(",", ";")},${l.strategy},${hits.size},${if ok then 1 else 0},${x.length},$steps,$pos,$attr,$txt\n")
          catch case e: Throwable => ()
      catch case e: Throwable => System.err.println(s"skip $s: $e")
    Files.write(out, sb.toString.getBytes(StandardCharsets.UTF_8))
    println(s"wrote $out")
