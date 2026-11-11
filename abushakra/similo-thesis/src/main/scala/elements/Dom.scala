//package elements
//
//// Represents a DOM node with an `element` and its `children`
//case class Dom(element: Element, children: List[Dom] = List.empty) {
//
//  // Insert a single Element into the tree, based on its xpath.chrome
//  def insert(insert: Element): Dom = {
//    val path = insert.xpath.chrome.stripPrefix("/").split("/").filter(_.nonEmpty).toSeq
//    recursiveInsert(insert, path)
//  }
//
//  private def recursiveInsert(insert: Element, path: Seq[String]): Dom = {
//    // Ensure path is compatible with pattern matching
//    val validPath: Seq[String] = path.toList
//
//    // Handle empty path
//    if (validPath.isEmpty) {
//      return this
//    }
//
//    // Helper Function: Extract base tag name (ignore [x] index)
//    def extractTagNameWithIndex(tag: String): (String, Option[Int]) = {
//      val regex = raw"([a-zA-Z0-9]+)(?:\[(\d+)\])?".r
//      tag match {
//        case regex(baseTag, index) => (baseTag, Option(index).map(_.toInt))
//        case _ => (tag, None)
//      }
//    }
//
//    validPath match {
//      // Base Case: Process a single tag in the path
//      case Seq(tag) =>
//        if (element.tag == extractTagName(tag)) {
//          val updatedChildren = children :+ Dom(insert)
//          this.copy(children = updatedChildren)
//        } else {
//          this
//        }
//
//      // Recursive Case: Process path segment -> Recursion
//      case tag :: tail =>
//        val (baseTag, index) = extractTagNameWithIndex(tag)
//        if (element.tag == baseTag) {
//          val (matchingChildren, nonMatchingChildren) = children.partition { child =>
//            child.element.tag == baseTag && child.element.index == index
//          }
//
//          val updatedChildren = if (matchingChildren.isEmpty) {
//            val newChild = Dom(Element.justTag(baseTag)).recursiveInsert(insert, tail)
//            List(newChild)
//          } else {
//            matchingChildren.map(_.recursiveInsert(insert, tail))
//          }
//
//          this.copy(children = updatedChildren ++ nonMatchingChildren)
//        } else {
//          this
//        }
//
//
//      // Fallback Case: Invalid path
//      case _ => this
//    }
//  }
//}
//
//object Dom {
//
//  // Build a full DOM tree from a flat list of elements
//  def apply(elements: Seq[Element]): Dom = {
//
//    // Assume root is <html>, as the DOM tree must start from the root
//    val root = Dom(Element.justTag("html"))
//
//    // Recursively insert all elements into the root
//    elements.foldLeft(root)((tree, el) => tree.insert(el))
//  }
//
//  def prettyPrintFullDom(dom: Dom, depth: Int = 0): String = {
//    // Create indentation based on depth for hierarchical display
//    val indentation = "  " * depth
//
//    // Extract the tag and XPath
//    val tag = dom.element.tag
//    val xpath = dom.element.xpath.chrome
//
//    // Format opening tag with XPath only
//    val openingTag = s"$indentation<$tag xpath=\"$xpath\">\n"
//
//    // Process children recursively
//    val childrenContent = dom.children.map(child => prettyPrintFullDom(child, depth + 1)).mkString
//
//    // Add the closing tag
//    val closingTag = s"$indentation</$tag>\n"
//
//    // Combine everything
//    openingTag + childrenContent + closingTag
//  }
//
//  // Find elements in the DOM by navigating with tag names extracted from the XPath
//  def findElementsByXPath(domm: Dom, xpath: String): Seq[Element] = {
//
//    // Helper function to extract tag names from the XPath
//    def extractTagsFromXPath(xpath: String): Seq[(String, Option[Int])] = {
//      xpath.stripPrefix("/")        // Strip the leading "/"
//        .split("/")                // Split the XPath into segments
//        .filter(_.nonEmpty)        // Ignore empty segments
//        .map { segment =>
//          val tagName = segment.takeWhile(_ != '[') // Extract tag name before '['
//          val position = "\\[(\\d+)\\]".r          // Regex to extract the [n] position
//            .findFirstMatchIn(segment)
//            .map(_.group(1).toInt)                // Convert position to Int if present
//          (tagName, position)                     // Return a tuple of tag and position
//        }
//        .toSeq
//    }
//
//    def navigateDom(currentDom: Dom, tagsToMatch: Seq[(String, Option[Int])]): Seq[Element] = {
//      // Debug: Show current DOM and tags to match
//      println(s"Current Dom: ${currentDom.element.tag}, Tags to Match: $tagsToMatch")
//
//      tagsToMatch match {
//        case Nil =>
//          // Debug: Base case - no more tags left to match
//          println(s"Reached matching element: ${currentDom.element.tag}")
//          Seq(currentDom.element) // Return the current DOM's element
//
//        case (tag, position) +: restOfTags =>
//          // Check if the current DOM's tag matches the expected tag
//          if (currentDom.element.tag == tag) {
//            println(s"Current Dom matches tag: ${tag}")
//            println(s"Children of the current Dom: ${currentDom.children.map(_.element.tag).mkString(", ")}")
//
//            val matchingChildren = if (restOfTags.isEmpty) {
//              // If this is the last tag in the XPath
//              currentDom.children.filter(_.element.tag == currentDom.element.tag.toUpperCase)
//            } else {
//              // Otherwise, match the tag as-is
//              currentDom.children.filter(_.element.tag == currentDom.element.tag)
//            }
//            // Handle the position if provided
//            val filteredChildren = position match {
//              // Filter children that match the current DOM's tag
//
//              case Some(pos) if pos > 0 && pos <= currentDom.children.size =>
//                println(s"Position specified: $pos, selecting child: ${matchingChildren(pos - 1).element.tag}")
//                Seq(matchingChildren(pos - 1)) // Get the (pos-1)th child (1-based index)
//
//              case Some(pos) =>
//                println(s"Position $pos out of bounds for children size ${matchingChildren.size}")
//                Seq.empty // Position out of bounds, return empty
//
//              case None =>
//                // No position specified, process all children
//                currentDom.children
//            }
//
//            // Recursively process filtered children
//            filteredChildren.flatMap(child => navigateDom(child, restOfTags))
//          }
//          else {
//            // Debug: Current Dom doesn't match the tag, return empty
//            println(s"Current Dom does not match tag '$tag', returning empty Seq")
//            Seq.empty
//          }
//      }
//    }
//
//    val tags = extractTagsFromXPath(xpath) // Extract tag names from the provided XPath
//    navigateDom(domm, tags)                // Start navigating the DOM
//  }
//
//}
