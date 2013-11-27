package org.http4s

import scala.language.postfixOps  // the http4s team resents importing this.

import play.api.libs.iteratee.Enumerator
import Status._

import scala.concurrent.duration._

import java.io.{FileInputStream,File,InputStreamReader}
import scala.concurrent.{Future, Await}
import org.scalatest.{Matchers, WordSpec}

/**
 * @author Bryce Anderson
 * Created on 2/14/13 at 8:44 PM
 */

class BodyParserSpec extends WordSpec with Matchers {
  import BodyParser._
  import concurrent.ExecutionContext.Implicits.global

  implicit def toBody[A](content: A)(implicit w: Writable[A]) = w.toBody(content)._1

  "xml" should {
    val server = new MockServer({
      case (body, req) => xml(req.charset)(body){ elem => Ok(elem.label) }
    })

    "parse the XML" in {
      val resp = Await.result(server(RequestPrelude(),
        Spool("<html><h1>h1</h1></html>")),
        2 seconds)

      resp.statusLine.code should equal (200)
      resp.body should equal ("html".getBytes)
    }

    "handle a parse failure" in {
      val resp = Await.result(server(RequestPrelude(), Spool("This is not XML.")), 2 seconds)
      resp.statusLine.code should equal (400)
    }
  }

//  "A File BodyParser" should {
//    val binData: Array[Byte] = "Bytes 10111".getBytes
//
//    def readFile(in: File): Array[Byte] = {
//      val os = new FileInputStream(in)
//      val data = new Array[Byte](in.length.asInstanceOf[Int])
//      os.read(data)
//      data
//    }
//
//    def readTextFile(in: File): String = {
//      val os = new InputStreamReader(new FileInputStream(in))
//      val data = new Array[Char](in.length.asInstanceOf[Int])
//      os.read(data,0,in.length.asInstanceOf[Int])
//      data.foldLeft("")(_ + _)
//    }
//
//    def mocServe(req: RequestPrelude, route: Route) = {
//      val server = new MockServer(route)
//      Await.result(server(req, Enumerator(BodyChunk(binData))), 2 seconds)
//    }
//
//    "Write a text file from a byte string" in {
//      val tmpFile = File.createTempFile("foo","bar")
//      val response = mocServe(RequestPrelude(), {
//        case req =>
//          BodyParser.textFile(req, tmpFile){
//            Ok("Hello")
//          }
//      })
//
//      readTextFile(tmpFile) should equal (new String(binData))
//      response.statusLine should equal (Status.Ok)
//      response.body should equal ("Hello".getBytes)
//    }
//
//    "Write a binary file from a byte string" in {
//      val tmpFile = File.createTempFile("foo","bar")
//      val response = mocServe(RequestPrelude(), {
//        case req =>
//          BodyParser.binFile(tmpFile)(Ok("Hello"))
//      })
//
//      response.statusLine should equal (Status.Ok)
//      response.body should equal ("Hello".getBytes)
//      readFile(tmpFile) should equal (binData)
//    }
//  }

}
