package org.http4s.client.blaze

import java.nio.ByteBuffer

import org.http4s._
import org.http4s.blaze.http.http_parser.Http1ClientParser
import scala.collection.mutable.ListBuffer

/** http/1.x parser for the blaze client */
private[blaze] object BlazeHttp1ClientParser {
  def apply(maxRequestLineSize: Int,
            maxHeaderLength: Int,
            maxChunkSize: Int,
            isLenient: Boolean): BlazeHttp1ClientParser =
    new BlazeHttp1ClientParser(maxRequestLineSize, maxHeaderLength, maxChunkSize, isLenient)
}

private[blaze] final class BlazeHttp1ClientParser(maxResponseLineSize: Int,
                                                  maxHeaderLength: Int,
                                                  maxChunkSize: Int,
                                                  isLenient: Boolean)
  extends Http1ClientParser(maxResponseLineSize, maxHeaderLength, 2*1024, maxChunkSize, isLenient) {
  private val headers = new ListBuffer[Header]
  private var status: Status = _
  private var httpVersion: HttpVersion = _

  override def reset(): Unit = {
    headers.clear()
    status = null
    httpVersion = null
    super.reset()
  }

  def getHttpVersion(): HttpVersion = {
    if (httpVersion == null) HttpVersion.`HTTP/1.0`  // TODO Questionable default
    else httpVersion
  }

  def doParseContent(buffer: ByteBuffer): Option[ByteBuffer] = Option(parseContent(buffer))

  def getHeaders(): Headers = {
    if (headers.isEmpty) Headers.empty
    else Headers(headers.result())
  }

  def getStatus(): Status = {
    if (status == null) Status.InternalServerError
    else status
  }

  def finishedResponseLine(buffer: ByteBuffer): Boolean =
    responseLineComplete() || parseResponseLine(buffer)

  def finishedHeaders(buffer: ByteBuffer): Boolean =
    headersComplete() || parseHeaders(buffer)

  override protected def submitResponseLine(code: Int,
                                            reason: String,
                                            scheme: String,
                                            majorversion: Int,
                                            minorversion: Int): Unit = {
    status = Status.fromIntAndReason(code, reason).valueOr(throw _)
    httpVersion = {
      if (majorversion == 1 && minorversion == 1)  HttpVersion.`HTTP/1.1`
      else if (majorversion == 1 && minorversion == 0)  HttpVersion.`HTTP/1.0`
      else HttpVersion.fromVersion(majorversion, minorversion).getOrElse(HttpVersion.`HTTP/1.0`)
    }
  }

  override protected def headerComplete(name: String, value: String): Boolean = {
    headers += Header(name, value)
    false
  }
}
