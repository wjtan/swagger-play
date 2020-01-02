/**
 * Copyright 2014 Reverb Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import java.io.StringWriter

import javax.xml.bind.annotation._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.swagger.v3.core.filter.SpecFilter
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.{OpenAPI, Paths}
import play.api.http.HttpEntity
import play.api.mvc._
import play.modules.swagger.{ApiListingCache, SwaggerPlugin}

import scala.jdk.CollectionConverters._
import javax.inject.Inject
import org.slf4j.{Logger, LoggerFactory}

object ErrorResponse {
  val ERROR = 1
  val WARNING = 2
  val INFO = 3
  val OK = 4
  val TOO_BUSY = 5
}

class ErrorResponse(@XmlElement var code: Int, @XmlElement var message: String) {
  def this() = this(0, null)

  @XmlTransient
  def getCode: Int = code

  def setCode(code: Int): Unit = this.code = code

  def getType: String = code match {
    case ErrorResponse.ERROR => "error"
    case ErrorResponse.WARNING => "warning"
    case ErrorResponse.INFO => "info"
    case ErrorResponse.OK => "ok"
    case ErrorResponse.TOO_BUSY => "too busy"
    case _ => "unknown"
  }

  def setType(`type`: String): Unit = {}

  def getMessage: String = message

  def setMessage(message: String): Unit = this.message = message
}

class ApiHelpController @Inject() (plugin: SwaggerPlugin, cc: ControllerComponents) extends SwaggerBaseApiController(plugin, cc) {
  def getResources: Action[AnyContent] = Action {
    request =>
      implicit val requestHeader: RequestHeader = request
      val host: String = requestHeader.host
      val resourceListing: OpenAPI = getResourceListing(host)
      val response: String =
        if (returnXml(request)) {
          toXmlString(resourceListing)
        } else {
          toJsonString(resourceListing)
        }
      returnValue(request, response)
  }

  def getResource(path: String): Action[AnyContent] = Action {
    request =>
      implicit val requestHeader: RequestHeader = request
      val host: String = requestHeader.host
      val apiListing: OpenAPI = getApiListing(path, host)
      val response: String =
        if (returnXml(request)) {
          toXmlString(apiListing)
        } else {
          toJsonString(apiListing)
        }
      Option(response) match {
        case Some(help) => returnValue(request, help)
        case None =>
          val msg = new ErrorResponse(500, "api listing for path " + path + " not found")
          logger.error(msg.message)
          if (returnXml(request)) {
            InternalServerError.chunked(Source.single(toXmlString(msg).getBytes("UTF-8"))).as("application/xml")
          } else {
            InternalServerError.chunked(Source.single(toJsonString(msg).getBytes("UTF-8"))).as("application/json")
          }
      }
  }
}

class SwaggerBaseApiController @Inject() (plugin: SwaggerPlugin, cc: ControllerComponents) extends AbstractController(cc)  {
  protected val logger: Logger = LoggerFactory.getLogger("play.modules.swagger")

  protected def returnXml(request: Request[_]): Boolean = request.path.contains(".xml")

  protected val AccessControlAllowOrigin: (String, String) = ("Access-Control-Allow-Origin", "*")

  /**
   * Get a list of all top level resources
   */
  protected def getResourceListing(host: String)(implicit requestHeader: RequestHeader): OpenAPI = {
    logger.debug("ApiHelpInventory.getRootResources")
    val docRoot = ""
    val queryParams = for ((key, value) <- requestHeader.queryString) yield {
      (key, value.toList.asJava)
    }
    val cookies = (for (cookie <- requestHeader.cookies) yield {
      (cookie.name, cookie.value)
    }).toMap
    val headers = for ((key, value) <- requestHeader.headers.toMap) yield {
      (key, value.toList.asJava)
    }

    val filter = new SpecFilter
    val specs: OpenAPI = plugin.apiListingCache.listing(host)

    plugin.specFilter match {
      case Some(f) => filter.filter(specs, f, queryParams.asJava, cookies.asJava, headers.asJava)
      case None => specs
    }

  }

  /**
   * Get detailed API/models for a given resource
   */
  protected def getApiListing(resourceName: String, host: String)(implicit requestHeader: RequestHeader): OpenAPI = {
    logger.debug("ApiHelpInventory.getResource(%s)".format(resourceName))
    val filter = new SpecFilter
    val queryParams = requestHeader.queryString.map { case (key, value) => key -> value.toList.asJava }
    val cookies = requestHeader.cookies.map { cookie => cookie.name -> cookie.value }.toMap.asJava
    val headers = requestHeader.headers.toMap.map { case (key, value) => key -> value.toList.asJava }
    val pathPart = resourceName

    val specs = plugin.apiListingCache.listing(host)

    val clone = plugin.specFilter match {
      case Some(f) => filter.filter(specs, f, queryParams.asJava, cookies, headers.asJava)
      case None => specs
    }
    val paths = clone.getPaths.asScala
    val filteredPaths = paths.view.filterKeys(_.startsWith(pathPart)).toMap
    val newPaths = new Paths()
    newPaths.putAll(filteredPaths.asJava)
    clone.setPaths(newPaths)
    clone
  }

  // TODO: looks like this is broken for anything other than strings
  def toXmlString(data: Any): String = {
    data match {
      case str: String => str
      case _ =>
        val stringWriter = new StringWriter()
        stringWriter.toString
    }
  }

  protected def XmlResponse(data: Any): Result = {
    val xmlValue = toXmlString(data)
    Ok.chunked(Source.single(xmlValue.getBytes("UTF-8"))).as("application/xml")
  }

  protected def returnValue(request: Request[_], obj: Any): Result = {
    val response =
      if (returnXml(request)) {
        XmlResponse(obj)
      } else {
        JsonResponse(obj)
      }
    response.withHeaders(AccessControlAllowOrigin)
  }

  def toJsonString(data: Any): String = {
    data match {
      case str: String => str
      case _ => Json.pretty(data.asInstanceOf[AnyRef])
    }
  }

  protected def JsonResponse(data: Any): Result = {
    val jsonBytes = toJsonString(data).getBytes("UTF-8")
    val source = Source.single(jsonBytes).map(ByteString.apply)
    Result(
      header = ResponseHeader(OK),
      body = HttpEntity.Streamed(source, Option(jsonBytes.length), None)
    ).as("application/json")
  }
}
