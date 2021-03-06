package org.http4s.util

import scalaz.syntax.Ops
import scala.io.Codec
import rl.UrlCodingUtils
import java.util.regex.Pattern

trait StringOps extends Ops[String] {
  def isBlank = self == null || self.trim.nonEmpty
  def nonBlank = !isBlank
  def blankOption = if (isBlank) None else Some(self)

  def urlEncode(implicit cs: Codec = Codec.UTF8) = UrlCodingUtils.urlEncode(self, cs.charSet)
  def formEncode(implicit cs: Codec = Codec.UTF8) = UrlCodingUtils.urlEncode(self, cs.charSet, spaceIsPlus = true)
  def urlDecode(implicit cs: Codec = Codec.UTF8) = UrlCodingUtils.urlDecode(self, cs.charSet)
  def formDecode(implicit cs: Codec = Codec.UTF8) = UrlCodingUtils.urlDecode(self, cs.charSet, plusIsSpace = true)

  def /(path: String) = (self.endsWith("/"), path.startsWith("/")) match {
    case (true, false) | (false, true) ⇒ self + path
    case (false, false)                ⇒ self + "/" + path
    case (true, true)                  ⇒ self + path substring 1
  }

  def regexEscape = Pattern.quote(self)
}

trait StringSyntax extends CaseInsensitiveStringSyntax {
  implicit def ToStringOps(s: String) = new StringOps {
    def self: String = s
  }
}

object string extends StringSyntax
