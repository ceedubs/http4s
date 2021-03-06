package org.http4s
package parser

import org.parboiled2._
import scalaz.Validation
import org.http4s.Header.Accept

private[parser] trait AcceptHeader {

  def ACCEPT(value: String): Validation[ParseErrorInfo, Accept] = new AcceptParser(value).parse

  private class AcceptParser(value: String) extends Http4sHeaderParser[Accept](value) with MediaParser {

    def entry: Rule1[Header.Accept] = rule {
      oneOrMore(FullRange).separatedBy("," ~ OptWS) ~ EOL ~> { xs: Seq[MediaRange] =>
        Header.Accept(xs.head, xs.tail: _*)}
    }

    def FullRange: Rule1[MediaRange] = rule {
      (MediaRangeDef ~ optional( QAndExtensions )) ~> {
        (mr: MediaRange, params: Option[(Q, Seq[(String, String)])]) =>
          params.map{ case (q, extensions) =>
            val m1 = if (q.intValue != Q.MAX_VALUE) mr.withQuality(q) else mr
            if (extensions.isEmpty) m1 else m1.withExtensions(extensions.toMap)
          }.getOrElse(mr)
      }
    }

    def QAndExtensions: Rule1[(Q, Seq[(String, String)])] = rule {
      AcceptParams | (oneOrMore(MediaTypeExtension) ~> {s: Seq[(String, String)] => (Q.Unity, s) })
    }

    def AcceptParams: Rule1[(Q, Seq[(String, String)])] = rule {
      (";" ~ OptWS ~ "q" ~ "=" ~ QValue ~ zeroOrMore(MediaTypeExtension)) ~> ((_:Q,_:Seq[(String, String)]))
    }
  }
}
