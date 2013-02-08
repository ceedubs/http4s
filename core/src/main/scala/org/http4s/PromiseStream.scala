// Resurrected and adapted from the late Akka PromiseStream.
// https://github.com/akka/akka/blob/3ececefb658b2b7a64894aa904380e4635635ef7/akka-actor/src/main/scala/akka/dispatch/PromiseStream.scala
package org.http4s

import java.util.concurrent.atomic.AtomicReference
import scala.util.continuations._
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Promise, Future}
import scala.util.Success

object PromiseStream {
  def apply[A](): PromiseStream[A] = new PromiseStream[A]()

  private sealed trait State
  private case object Normal extends State
  private case object Pending extends State
  private case object Busy extends State
}

trait PromiseStreamOut[A] {
  self ⇒

  def dequeue()(implicit executionContext: ExecutionContext): Future[A]

  def dequeue(promise: Promise[A])(implicit executionContext: ExecutionContext): Future[A]

  def apply()(implicit executionContext: ExecutionContext): A @cps[Future[Any]]

  def apply(promise: Promise[A])(implicit executionContext: ExecutionContext): A @cps[Future[Any]]

  final def map[B](f: (A) ⇒ B): PromiseStreamOut[B] = new PromiseStreamOut[B] {

    def dequeue()(implicit executionContext: ExecutionContext): Future[B] = self.dequeue().map(f)

    def dequeue(promise: Promise[B])(implicit executionContext: ExecutionContext): Future[B] =
      self.dequeue().flatMap(a ⇒ promise.complete(Success(f(a))).future)

    def apply()(implicit executionContext: ExecutionContext): B @cps[Future[Any]] = this.dequeue().apply()

    def apply(promise: Promise[B])(implicit executionContext: ExecutionContext): B @cps[Future[Any]] =
      this.dequeue(promise).apply()
  }
}

trait PromiseStreamIn[A] {

  def enqueue(elem: A): Unit

  final def enqueue(elem1: A, elem2: A, elems: A*): Unit =
    this += elem1 += elem2 ++= elems

  final def enqueue(elem: Future[A])(implicit executionContext: ExecutionContext): Unit =
    elem foreach (enqueue(_))

  final def enqueue(elem1: Future[A], elem2: Future[A], elems: Future[A]*)(implicit executionContext: ExecutionContext) {
    this += elem1 += elem2
    elems foreach (enqueue(_))
  }

  final def +=(elem: A): this.type = {
    enqueue(elem)
    this
  }

  final def +=(elem1: A, elem2: A, elems: A*): this.type = {
    enqueue(elem1, elem2, elems: _*)
    this
  }

  final def +=(elem: Future[A])(implicit executionContext: ExecutionContext): this.type = {
    enqueue(elem)
    this
  }

  final def +=(elem1: Future[A], elem2: Future[A], elems: Future[A]*)(implicit executionContext: ExecutionContext): this.type = {
    enqueue(elem1, elem2, elems: _*)
    this
  }

  final def ++=(elem: Traversable[A]): this.type = {
    elem foreach enqueue
    this
  }

  final def ++=(elem: Future[Traversable[A]])(implicit executionContext: ExecutionContext): this.type = {
    elem foreach (this ++= _)
    this
  }

  def <<(elem: A): PromiseStreamIn[A] @cps[Future[Any]]

  def <<(elem1: A, elem2: A, elems: A*): PromiseStreamIn[A] @cps[Future[Any]]

  def <<(elem: Future[A])(implicit executionContext: ExecutionContext): PromiseStreamIn[A] @cps[Future[Any]]

  def <<(elem1: Future[A], elem2: Future[A], elems: Future[A]*)(implicit executionContext: ExecutionContext): PromiseStreamIn[A] @cps[Future[Any]]

  def <<<(elems: Traversable[A]): PromiseStreamIn[A] @cps[Future[Any]]

  def <<<(elems: Future[Traversable[A]])(implicit executionContext: ExecutionContext): PromiseStreamIn[A] @cps[Future[Any]]

}

class PromiseStream[A] extends PromiseStreamOut[A] with PromiseStreamIn[A] {
  import PromiseStream.{ State, Normal, Pending, Busy }

  private val _elemOut: AtomicReference[List[A]] = new AtomicReference(Nil)
  private val _elemIn: AtomicReference[List[A]] = new AtomicReference(Nil)
  private val _pendOut: AtomicReference[List[Promise[A]]] = new AtomicReference(null)
  private val _pendIn: AtomicReference[List[Promise[A]]] = new AtomicReference(null)
  private val _state: AtomicReference[State] = new AtomicReference(Normal)

  @tailrec
  final def apply()(implicit executionContext: ExecutionContext): A @cps[Future[Any]] =
    if (_state.get eq Normal) {
      val eo = _elemOut.get
      if (eo eq null) apply()
      else {
        if (eo.nonEmpty) {
          if (_elemOut.compareAndSet(eo, eo.tail)) shift { cont: (A ⇒ Future[Any]) ⇒ cont(eo.head) }
          else apply()
        } else apply(Promise[A])
      }
    } else apply(Promise[A])

  final def apply(promise: Promise[A])(implicit executionContext: ExecutionContext): A @cps[Future[Any]] =
    shift { cont: (A ⇒ Future[Any]) ⇒ dequeue(promise) flatMap cont }

  @tailrec
  final def enqueue(elem: A): Unit = _state.get match {
    case Normal ⇒
      val ei = _elemIn.get
      if (ei eq null) enqueue(elem)
      else if (!_elemIn.compareAndSet(ei, elem :: ei)) enqueue(elem)

    case Pending ⇒
      val po = _pendOut.get
      if (po eq null) enqueue(elem)
      else {
        if (po.isEmpty) {
          if (_state.compareAndSet(Pending, Busy)) {
            var nextState: State = Pending
            try {
              val pi = _pendIn.get
              if (pi ne null) {
                if (pi.isEmpty) {
                  if (_pendIn.compareAndSet(Nil, null)) {
                    if (_pendOut.compareAndSet(Nil, null)) {
                      _elemIn.set(Nil)
                      _elemOut.set(List(elem))
                      nextState = Normal
                    } else {
                      _pendIn.set(Nil)
                    }
                  }
                } else {
                  if (_pendOut.get eq Nil) _pendOut.set(_pendIn.getAndSet(Nil).reverse)
                }
              }
            } finally {
              _state.set(nextState)
            }
            if (nextState eq Pending) enqueue(elem)
          } else enqueue(elem)
        } else {
          if (_pendOut.compareAndSet(po, po.tail)) {
            po.head success elem
            if (!po.head.isCompleted) enqueue(elem)
          } else enqueue(elem)
        }
      }

    case Busy ⇒
      enqueue(elem)
  }

  @tailrec
  final def dequeue()(implicit executionContext: ExecutionContext): Future[A] =
    if (_state.get eq Normal) {
      val eo = _elemOut.get
      if (eo eq null) dequeue()
      else {
        if (eo.nonEmpty) {
          if (_elemOut.compareAndSet(eo, eo.tail)) Promise.successful(eo.head).future
          else dequeue()
        } else dequeue(Promise[A])
      }
    } else dequeue(Promise[A])

  @tailrec
  final def dequeue(promise: Promise[A])(implicit executionContext: ExecutionContext): Future[A] = _state.get match {
    case Pending ⇒
      val pi = _pendIn.get
      if ((pi ne null) && _pendIn.compareAndSet(pi, promise :: pi)) promise.future else dequeue(promise)

    case Normal ⇒
      val eo = _elemOut.get
      if (eo eq null) dequeue(promise)
      else {
        if (eo.isEmpty) {
          if (_state.compareAndSet(Normal, Busy)) {
            var nextState: State = Normal
            try {
              val ei = _elemIn.get
              if (ei ne null) {
                if (ei.isEmpty) {
                  if (_elemIn.compareAndSet(Nil, null)) {
                    if (_elemOut.compareAndSet(Nil, null)) {
                      _pendIn.set(Nil)
                      _pendOut.set(List(promise))
                      nextState = Pending
                    } else {
                      _elemIn.set(Nil)
                    }
                  }
                } else {
                  if (_elemOut.get eq Nil) _elemOut.set(_elemIn.getAndSet(Nil).reverse)
                }
              }
            } finally {
              _state.set(nextState)
            }
            if (nextState eq Normal) dequeue(promise)
            else promise.future
          } else dequeue(promise)
        } else {
          if (_elemOut.compareAndSet(eo, eo.tail)) {
            promise.success(eo.head).future
          } else dequeue(promise)
        }
      }

    case Busy ⇒
      dequeue(promise)
  }

  final def <<(elem: A): PromiseStream[A] @cps[Future[Any]] =
    shift { cont: (PromiseStream[A] ⇒ Future[Any]) ⇒ cont(this += elem) }

  final def <<(elem1: A, elem2: A, elems: A*): PromiseStream[A] @cps[Future[Any]] =
    shift { cont: (PromiseStream[A] ⇒ Future[Any]) ⇒ cont(this += (elem1, elem2, elems: _*)) }

  final def <<(elem: Future[A])(implicit executionContext: ExecutionContext): PromiseStream[A] @cps[Future[Any]] =
    shift { cont: (PromiseStream[A] ⇒ Future[Any]) ⇒ elem map (a ⇒ cont(this += a)) }

  final def <<(elem1: Future[A], elem2: Future[A], elems: Future[A]*)(implicit executionContext: ExecutionContext): PromiseStream[A] @cps[Future[Any]] =
    shift { cont: (PromiseStream[A] ⇒ Future[Any]) ⇒
      val seq = Future.sequence(elem1 +: elem2 +: elems)
      seq map (a ⇒ cont(this ++= a))
    }

  final def <<<(elems: Traversable[A]): PromiseStream[A] @cps[Future[Any]] =
    shift { cont: (PromiseStream[A] ⇒ Future[Any]) ⇒ cont(this ++= elems) }

  final def <<<(elems: Future[Traversable[A]])(implicit executionContext: ExecutionContext): PromiseStream[A] @cps[Future[Any]] =
    shift { cont: (PromiseStream[A] ⇒ Future[Any]) ⇒ elems map (as ⇒ cont(this ++= as)) }
}