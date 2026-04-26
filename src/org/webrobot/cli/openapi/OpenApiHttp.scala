package org.webrobot.cli.openapi

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import eu.webrobot.openapi.client.{ApiClient, Pair}

import java.util

/** Invoca [[ApiClient.invokeAPI]] con `TypeReference[JsonNode]]` (il `DefaultApi` generato non deserializza). */
object OpenApiHttp {

  private val emptyColl = new util.ArrayList[Pair]()
  private def emptyHeaders = new util.HashMap[String, String]()
  private def emptyCookies = new util.HashMap[String, String]()
  private def emptyForm = new util.HashMap[String, Object]()

  def getJson(c: ApiClient, path: String): JsonNode =
    invoke(c, "GET", path, emptyColl, "", null)

  def getJson(c: ApiClient, path: String, queryParams: util.List[Pair]): JsonNode =
    invoke(c, "GET", path, queryParams, "", null)

  def deleteJson(c: ApiClient, path: String): JsonNode =
    invoke(c, "DELETE", path, emptyColl, "", null)

  def postJson(c: ApiClient, path: String, body: AnyRef): JsonNode =
    invoke(c, "POST", path, emptyColl, "", body)

  def putJson(c: ApiClient, path: String, body: AnyRef): JsonNode =
    invoke(c, "PUT", path, emptyColl, "", body)

  def pairs(c: ApiClient, tuples: (String, AnyRef)*): util.List[Pair] = {
    val out = new util.ArrayList[Pair]()
    tuples.foreach {
      case (k, v) =>
        if (v != null) out.addAll(c.parameterToPair(k, v))
    }
    out
  }

  private def invoke(
      c: ApiClient,
      method: String,
      path: String,
      queryParams: util.List[Pair],
      urlQuery: String,
      body: AnyRef
  ): JsonNode = {
    c.invokeAPI(
      path,
      method,
      queryParams,
      emptyColl,
      if (urlQuery == null) "" else urlQuery,
      body,
      emptyHeaders,
      emptyCookies,
      emptyForm,
      "application/json",
      "application/json",
      Array.empty[String],
      new TypeReference[JsonNode]() {}
    )
  }
}
