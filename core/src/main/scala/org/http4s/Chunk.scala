package org.http4s

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.ByteBuffer
import java.nio.charset.Charset

import scala.collection.generic.CanBuildFrom
import scala.collection.{IndexedSeqOptimized, SeqProxy, mutable, IndexedSeqLike}
import scala.reflect.ClassTag
import scala.annotation.tailrec
import scala.io.Codec

import scalaz.{ImmutableArray, RopeBuilder, Rope}
import scalaz.stream.{BytesBuilder, Bytes}


sealed trait Chunk extends IndexedSeq[Byte] {
  def decodeString(charset: Charset): String = new String(toArray, charset)

  def decodeString(charset: CharacterSet): String = decodeString(charset.charset)

  /**
   * Returns a ByteBuffer that wraps an array copy of this BodyChunk
   */
  def asByteBuffer: ByteBuffer = ByteBuffer.wrap(toArray)
}

class BodyChunk private (private val self: Bytes) extends Chunk with IndexedSeqOptimized[Byte, BodyChunk] {

  override def iterator: Iterator[Byte] = self.iterator

  override def reverseIterator: Iterator[Byte] = self.reverseIterator

  override def apply(idx: Int): Byte = self(idx)

  def length: Int = self.length

  override def copyToArray[B >: Byte](xs: Array[B], start: Int, len: Int): Unit = self.copyToArray(xs, start, len)

  override protected[this] def newBuilder: mutable.Builder[Byte, BodyChunk] = BodyChunk.newBuilder

  def asInputStream: InputStream = new ByteArrayInputStream(toArray)

  def ++(b: BodyChunk): BodyChunk = BodyChunk(self ++ b.self)

  override def splitAt(index: Int): (BodyChunk, BodyChunk) = {
    val (left, right) = self.splitAt(index)
    (BodyChunk(left), BodyChunk(right))
  }

  override def toString(): String = s"BodyChunk(${length} bytes)"
}

object BodyChunk {
  type Builder = mutable.Builder[Byte, BodyChunk]

  def apply(bytes: Bytes): BodyChunk = new BodyChunk(bytes)

  def apply(rope: Rope[Byte]): BodyChunk = BodyChunk(rope.toArray)

  def apply(bytes: Array[Byte]): BodyChunk = BodyChunk(Bytes.of(bytes))

  def apply(bytes: Byte*): BodyChunk = BodyChunk(bytes.toArray)

  def apply(bytes: ByteBuffer): BodyChunk = {
    val pos = bytes.position()
    val rem = bytes.remaining()
    val n = new Array[Byte](rem)
    System.arraycopy(bytes.array(), pos, n, 0, rem)
    BodyChunk(n)
  }

  def apply(string: String): BodyChunk = apply(string, Codec.UTF8.charSet)

  def apply(string: String, charset: Charset): BodyChunk = BodyChunk(string.getBytes(charset))

  def fromArray(array: Array[Byte], offset: Int, length: Int): BodyChunk = BodyChunk(array.slice(offset, length))

  val empty: BodyChunk = BodyChunk(Bytes.empty)

  private def newBuilder: Builder = (new BytesBuilder).mapResult(BodyChunk.apply _)

  implicit def canBuildFrom: CanBuildFrom[TraversableOnce[Byte], Byte, BodyChunk] =
    new CanBuildFrom[TraversableOnce[Byte], Byte, BodyChunk] {
      def apply(from: TraversableOnce[Byte]): Builder = newBuilder
      def apply(): Builder = newBuilder
    }
}

case class TrailerChunk(headers: HeaderCollection = HeaderCollection.empty) extends Chunk {

  def ++(chunk: TrailerChunk): TrailerChunk = {
    TrailerChunk(chunk.headers.foldLeft(headers)((headers, h) => headers.put(h)))
  }

  override def iterator: Iterator[Byte] = Iterator.empty

  override def toArray[B >: Byte : ClassTag]: Array[B] = Array.empty[B]

  override def apply(idx: Int): Byte = throw new IndexOutOfBoundsException("Trailer chunk doesn't contain bytes.")

  def length: Int = 0
}
