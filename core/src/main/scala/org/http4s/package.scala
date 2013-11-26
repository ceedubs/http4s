package org

import http4s.ext.Http4sString
import play.api.libs.iteratee.{Enumeratee, Iteratee}
import scala.language.implicitConversions
import com.typesafe.config.{ConfigFactory, Config}
import org.joda.time.{DateTime, DateTimeZone, ReadableInstant}
import org.joda.time.format.DateTimeFormat
import java.util.Locale
import org.http4s.util.CaseInsensitiveStringSyntax

import scala.concurrent.Future

package object http4s extends CaseInsensitiveStringSyntax {
  type Route = PartialFunction[(Body, RequestPrelude), Future[Response]]

  type Body = Future[Spool[Chunk]]

  type Middleware = (Route => Route)

  private[http4s] implicit def string2Http4sString(s: String) = new Http4sString(s)

  protected[http4s] val Http4sConfig: Config = ConfigFactory.load()

  private[this] val Rfc1123Format = DateTimeFormat
    .forPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'")
    .withLocale(Locale.US)
    .withZone(DateTimeZone.UTC);

  implicit class RichReadableInstant(instant: ReadableInstant) {
    def formatRfc1123: String = Rfc1123Format.print(instant)
  }

  val UnixEpoch = new DateTime(0)
}
