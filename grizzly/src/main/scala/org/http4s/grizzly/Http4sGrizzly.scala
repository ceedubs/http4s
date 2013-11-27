//package org.http4s
//package grizzly
//
//import org.glassfish.grizzly.http.server.{Response=>GrizResp,Request=>GrizReq, HttpHandler}
//
//import java.net.InetAddress
//import scala.collection.JavaConverters._
//import concurrent.{ExecutionContext}
//import play.api.libs.iteratee.{Concurrent, Done}
//import org.http4s.Status.{InternalServerError, NotFound}
//import org.glassfish.grizzly.ReadHandler
//import scala.util.Try
//
///**
// * @author Bryce Anderson
// */
//
//class Http4sGrizzly(route: Route, chunkSize: Int = 32 * 1024)(implicit executor: ExecutionContext = ExecutionContext.global) extends HttpHandler {
//
//  override def service(req: GrizReq, resp: GrizResp) {
//    resp.suspend()  // Suspend the response until we close it
//    val request = toRequest(req)
//    val parser = try {
//      route.lift(request).getOrElse(Done(NotFound(request)))
//    } catch { case t: Throwable => Done[Chunk, Response](InternalServerError(t)) }
//
//    val handler = parser.flatMap { response =>
//      resp.setStatus(response.prelude.status.code, response.prelude.status.reason)
//      for (header <- response.prelude.headers)
//        resp.addHeader(header.name.toString, header.value)
//
//      val isChunked = response.isChunked
//      val out = new OutputIteratee(resp.getNIOOutputStream, isChunked)
//      response.body.transform(out)
//    }
//
//    var canceled = false
//    Concurrent.unicast[Chunk]({
//      channel =>
//        val bytes = new Array[Byte](chunkSize)
//        val is = req.getNIOInputStream
//        def push() = {
//          while(is.available() > 0 && (!canceled)) {
//            val readBytes = is.read(bytes,0,chunkSize)
//            channel.push(BodyChunk.fromArray(bytes, 0, readBytes))
//          }
//        }
//        is.notifyAvailable( new ReadHandler { self =>
//          def onDataAvailable() { push(); is.notifyAvailable(self) }
//          def onError(t: Throwable) {}
//          def onAllDataRead() { push(); channel.eofAndEnd() }
//        })
//      },
//      {canceled = true}
//    ).run(handler)
//    .onComplete{ _ => resp.resume() }
//  }
//
//  protected def toRequest(req: GrizReq): RequestPrelude = {
//    val input = req.getNIOInputStream
//    RequestPrelude(
//      requestMethod = Method(req.getMethod.toString),
//      scriptName = req.getContextPath, // + req.getServletPath,
//      pathInfo = Option(req.getPathInfo).getOrElse(""),
//      queryString = Option(req.getQueryString).getOrElse(""),
//      protocol = ServerProtocol(req.getProtocol.getProtocolString),
//      headers = toHeaders(req),
//      urlScheme = HttpUrlScheme(req.getScheme),
//      serverName = req.getServerName,
//      serverPort = req.getServerPort,
//      serverSoftware = ServerSoftware(req.getServerName),
//      remote = InetAddress.getByName(req.getRemoteAddr) // TODO using remoteName would trigger a lookup
//    )
//  }
//
//  protected def toHeaders(req: GrizReq): HeaderCollection = {
//    val headers = for {
//      name <- req.getHeaderNames.asScala
//      value <- req.getHeaders(name).asScala
//    } yield Header(name, value)
//    HeaderCollection(headers.toSeq : _*)
//  }
//}
