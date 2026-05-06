package org.webrobot.cli.plugin

import WebRobot.Cli.Sdk.openapi.{GenericApiException, GenericClient}
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.`type`.TypeFactory
import com.fasterxml.jackson.databind.{ObjectMapper}
import eu.webrobot.cli.sdk.{WebroApiClient, WebroApiException}

import java.util
import java.util.stream.Stream
import scala.collection.JavaConverters._

/**
 * Implementation of the public WebroApiClient interface (from webrobot-cli-sdk v0.2.0)
 * that delegates to GenericClient (from org.webrobot.sdk). Translates
 * GenericApiException into the public WebroApiException hierarchy so partner plugins
 * never see proprietary types.
 */
final class CliApiClient(generic: GenericClient) extends WebroApiClient {

  private val mapper = new ObjectMapper()

  override def get(path: String, queryParams: util.Map[String, AnyRef]): JsonNode =
    safe { generic.get(path, queryParams) }

  override def post(path: String, body: AnyRef): JsonNode =
    safe { generic.post(path, body) }

  override def put(path: String, body: AnyRef): JsonNode =
    safe { generic.put(path, body) }

  override def patch(path: String, body: AnyRef): JsonNode =
    safe { generic.patch(path, body) }

  override def delete(path: String): JsonNode =
    safe { generic.delete(path) }

  override def stream(path: String, queryParams: util.Map[String, AnyRef]): Stream[JsonNode] =
    safe { generic.stream(path, queryParams) }

  override def get[T](path: String, queryParams: util.Map[String, AnyRef], `type`: Class[T]): T =
    safe { generic.get(path, queryParams, `type`) }

  override def getList[T](path: String, queryParams: util.Map[String, AnyRef], `type`: Class[T]): util.List[T] =
    safe { generic.getList(path, queryParams, `type`) }

  // ── Error translation ──────────────────────────────────────────────────────

  private def safe[T](block: => T): T =
    try block
    catch {
      case e: GenericApiException => throw mapException(e)
    }

  private def mapException(e: GenericApiException): WebroApiException = {
    val flatHeaders: util.Map[String, String] = new util.HashMap[String, String]()
    if (e.headers() != null)
      e.headers().asScala.foreach { case (k, v) =>
        if (v != null && !v.isEmpty) flatHeaders.put(k, v.get(0))
      }

    val sc      = e.statusCode()
    val body    = e.errorBody()
    val msg     = if (e.getMessage != null) e.getMessage else s"HTTP $sc"
    val reqId   = e.requestId()

    sc match {
      case 401             => new WebroApiException.Auth(sc, body, flatHeaders, reqId, msg)
      case 403             => new WebroApiException.Forbidden(sc, body, flatHeaders, reqId, msg)
      case 404             => new WebroApiException.NotFound(sc, body, flatHeaders, reqId, msg)
      case 400 | 422       => new WebroApiException.Validation(sc, body, flatHeaders, reqId, msg)
      case s if s >= 500   => new WebroApiException.Server(sc, body, flatHeaders, reqId, msg)
      case _               => new WebroApiException(sc, body, flatHeaders, reqId, msg)
    }
  }
}
