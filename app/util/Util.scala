package util

import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.text.Normalizer.Form
import java.util.Locale
import java.util.regex.Pattern

import org.joda.time.DateTime


object Util {

  val version = new DateTime()

  def isNumeric(input: String): Boolean = input.forall(_.isDigit)

  def sortByOrder(currentSortBy: String, currentOrder: String, newSortByOpt: Option[String]): (String, String) = {

    val sortBy = newSortByOpt.getOrElse(currentSortBy)
    val order = newSortByOpt.map { newSortBy =>
      if (currentSortBy.equals(sortBy)) {
        if (currentOrder == "asc") {
          "desc"
        } else {
          "asc"
        }
      } else {
        "desc"
      }
    }.getOrElse(currentOrder)

    (sortBy, order)

  }


  def toSlug(input: String) = {
    val NONLATIN = Pattern.compile("[^\\w-]")
    val WHITESPACE = Pattern.compile("[\\s]")
    val nowhitespace = WHITESPACE.matcher(input).replaceAll("-")
    val normalized = Normalizer.normalize(nowhitespace, Form.NFD)
    val slug = NONLATIN.matcher(normalized).replaceAll("")
    slug.toLowerCase(Locale.ENGLISH)
  }


  def isBlank(s: String) = {
    s == null || s.isEmpty
  }

  def tokenHash(input: String) = java.util.Base64.getEncoder.encodeToString(("someSecureSalt" + input).getBytes(StandardCharsets.UTF_8))


  // Need to compare content equality and None = None
  def optionCompare(a: Option[String], b: Option[String]) = (a, b) match {
    case (Some(x), Some(y)) => x == y
    case (None, None) => true
    case _ => false
  }


}


