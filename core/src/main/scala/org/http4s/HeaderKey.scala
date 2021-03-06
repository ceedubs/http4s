package org.http4s

import scalaz.NonEmptyList
import scala.annotation.tailrec
import scala.reflect.ClassTag
import org.http4s.Header.RawHeader
import org.http4s.util.CaseInsensitiveString
import org.http4s.util.string._

/**
 * @author Bryce Anderson
 *         Created on 11/3/13
 */
sealed trait HeaderKey {
  type HeaderT <: Header

  def name: CaseInsensitiveString

  def matchHeader(header: Header): Option[HeaderT]
  def unapply(header: Header): Option[HeaderT] = matchHeader(header)

  override def toString: String = s"HeaderKey(${name}})"
}

sealed trait ExtractableHeaderKey extends HeaderKey {
  def from(headers: Headers): Option[HeaderT]
  def unapply(headers: Headers): Option[HeaderT] = from(headers)
}

/**
 * Represents a Header that should not be repeated.
 */
trait SingletonHeaderKey extends ExtractableHeaderKey {
  def from(headers: Headers): Option[HeaderT] = headers.collectFirst(Function.unlift(matchHeader))
}

/**
 * Represents a header key whose multiple headers can be combined by joining
 * their values with a comma.  See RFC 2616, Section 4.2.
 *
 */
trait RecurringHeaderKey extends ExtractableHeaderKey { self =>
  type HeaderT <: RecurringHeader
  type GetT = Option[HeaderT]
  def apply(values: NonEmptyList[HeaderT#Value]): HeaderT
  def apply(first: HeaderT#Value, more: HeaderT#Value*): HeaderT = apply(NonEmptyList.apply(first, more: _*))
  def from(headers: Headers): Option[HeaderT] = {
    @tailrec def loop(hs: Headers, acc: NonEmptyList[HeaderT#Value]): NonEmptyList[HeaderT#Value] =
      if (hs.nonEmpty) matchHeader(hs.head) match {
        case Some(header) => loop(hs.tail, acc append header.values)
        case None => loop(hs.tail, acc)
      }
      else acc
    @tailrec def start(hs: Headers): Option[HeaderT] =
      if (hs.nonEmpty) matchHeader(hs.head) match {
        case Some(header) => Some(apply(loop(hs.tail, header.values)))
        case None => start(hs.tail)
      }
      else None
    start(headers)
  }
}

private[http4s] abstract class InternalHeaderKey[T <: Header : ClassTag] extends HeaderKey {
  type HeaderT = T

  val name = getClass.getName.split("\\.").last.replaceAll("\\$minus", "-").split("\\$").last.replace("\\$$", "").ci

  private val runtimeClass = implicitly[ClassTag[HeaderT]].runtimeClass

  override def matchHeader(header: Header): Option[HeaderT] = {
    if (runtimeClass.isInstance(header)) Some(header.asInstanceOf[HeaderT])
    else if (header.isInstanceOf[RawHeader] && name == header.name && runtimeClass.isInstance(header.parsed))
      Some(header.parsed.asInstanceOf[HeaderT])
    else None
  }
}

private[http4s] trait StringHeaderKey extends SingletonHeaderKey {
  type HeaderT = Header

  override def matchHeader(header: Header): Option[HeaderT] = {
    if (header.name == name) Some(header)
    else None
  }

  override def from(headers: Headers): Option[HeaderT] = headers.find(_ is this)
}

private[http4s] trait DefaultHeaderKey extends InternalHeaderKey[Header] with StringHeaderKey {
  override type HeaderT = Header
}
