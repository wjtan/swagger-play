package play.modules.swagger

//import io.swagger.v3.oas.annotations._

import io.swagger.v3.oas.annotations.{Operation => ApiOperation}
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.util.AnnotationsUtils
import io.swagger.v3.oas.models._
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.headers._
import io.swagger.v3.oas.models.info._
import io.swagger.v3.oas.models.links.Link
import io.swagger.v3.oas.models.tags.Tag
import io.swagger.v3.oas.models.media._
import io.swagger.v3.oas.models.parameters._
import io.swagger.v3.oas.models.parameters.{Parameter => ApiParameter}
import io.swagger.v3.oas.models.responses._
import io.swagger.v3.oas.models.security._
import io.swagger.v3.oas.models.servers._
import javax.inject.Singleton
//import io.swagger.v3.core.util.BaseReaderUtils
import io.swagger.v3.core.util.Json
import io.swagger.v3.core.util.ParameterProcessor
import io.swagger.v3.core.util.PrimitiveType
import io.swagger.v3.core.util.ReflectionUtils
import org.apache.commons.lang3.StringUtils
import com.typesafe.scalalogging._
import play.modules.swagger.util.CrossUtil
import play.routes.compiler._

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._
import java.lang.annotation.Annotation
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.util.regex.Pattern

import javax.inject.Inject

@Singleton
class PlayReaderProvider @Inject() (routes: RouteWrapper, config: PlaySwaggerConfig) {
  def get(api: OpenAPI): PlayReader = new  PlayReader(api, routes, config)
}

class PlayReader (api: OpenAPI, routes: RouteWrapper, config: PlaySwaggerConfig) {
  private[this] val logger = Logger[PlayReader]

  private[this] val typeFactory = Json.mapper.getTypeFactory
  private[this] val modelConverters = ModelConverters.getInstance

  val SUCCESSFUL_OPERATION = "successful operation"
  val MEDIA_TYPE = "application/json"

  import StringUtils._

  def read(classes: List[Class[_]]): Unit = {
    // process SwaggerDefinitions first - so we get tags in desired order
    for (cls <- classes) {
      val definition = ReflectionUtils.getAnnotation(cls, classOf[io.swagger.v3.oas.annotations.OpenAPIDefinition])
      if (definition != null) {
        parseApiDefinition(definition)
      }
    }

    for (cls <- classes) {
      read(cls)
    }
  }

  def read(cls: Class[_]): Unit = read(cls, readHidden = false)

  def read(cls: Class[_], readHidden: Boolean): Unit = {
    val hidden = ReflectionUtils.getAnnotation(cls, classOf[io.swagger.v3.oas.annotations.Hidden])

    //val tags = scala.collection.mutable.Map.empty[String, Tag]
    //val securities = ListBuffer.empty[SecurityRequirement]
    //var consumes = new Array[String](0)
    //var produces = new Array[String](0)
    //val globalSchemes = scala.collection.mutable.Set.empty[Schemes]

    //val readable = (api != null && readHidden) || (api != null && !api.hidden())
    val readable = readHidden || hidden == null

    // TODO possibly allow parsing also without @Api annotation
    if (!readable) {
      return
    }
    // the value will be used as a tag for 2.0 UNLESS a Tags annotation is present
    //          val tagStrings = extractTags(api)
    //          for (tagString <- tagStrings) {
    //              val tag = new Tag().name(tagString)
    //              tags.put(tagString, tag)
    //          }
    //          for (tagName <- tags.keys) {
    //              swagger.tag(tags(tagName))
    //          }
    //
    //          if (!isEmpty(api.produces)) {
    //              produces = toArray(api.produces)
    //          }
    //          if (!isEmpty(api.consumes)) {
    //              consumes = toArray(api.consumes)
    //          }
    //          globalSchemes ++= parseSchemes(api.protocols)
    //          val authorizations = api.authorizations()
    //
    //          for (auth <- authorizations) {
    //              if (!isEmpty(auth.value)) {
    //                  val scopes = auth.scopes
    //                  val addedScopes = scopes.toList.map(_.scope).filter(!isEmpty(_))
    //                  val security = new SecurityRequirement().requirement(auth.value, addedScopes.asJava)
    //
    //                  securities += security
    //              }
    //          }

    // parse the method
    val methods = cls.getMethods
    for (method <- methods) {
      readMethod(cls, method, api: OpenAPI)
    }
  }

  private def readMethod(cls: Class[_], method: Method, api: OpenAPI): Unit = {
    if (ReflectionUtils.isOverriddenMethod(method, cls)) return

    // complete name as stored in route
    val fullMethodName = getFullMethodName(cls, method)

    if (!routes.exists(fullMethodName)) return

    val route = routes(fullMethodName)
    val operationPath = getPathFromRoute(route.path, config.basePath)
    if (operationPath == null) return

    val apiOperation = ReflectionUtils.getAnnotation(method, classOf[io.swagger.v3.oas.annotations.Operation])
    if (apiOperation == null) return

    val httpMethod = extractOperationMethod(apiOperation, method, route)
    if (httpMethod == null) return

    val operation = parseMethod(cls, method, route, apiOperation)

    val path: PathItem =
      if (api.getPaths.containsKey(operationPath)) {
        api.getPaths.get(operationPath)
      } else {
        val path = new PathItem()
        api.path(operationPath, path)
        path
      }

    path.operation(httpMethod, operation)
  }


  //      //readImplicitParameters(method, operation, cls)
  //    }
  //  }

  //                      for (scheme <- parseSchemes(apiOperation.protocols())) {
  //                          operation.scheme(scheme)
  //                      }
  //                  }
  //
  //                  if (operation.getSchemes == null || operation.getSchemes.isEmpty) {
  //                      for (scheme <- globalSchemes) {
  //                          operation.scheme(scheme)
  //                      }
  //                  }
  //                  // can't continue without a valid http method
  //                  if (httpMethod != null) {
  //                      if (apiOperation != null) {
  //                          for (tag <- apiOperation.tags) {
  //                              if (!"".equals(tag)) {
  //                                  operation.tag(tag)
  //                                  swagger.tag(new Tag().name(tag))
  //                              }
  //                          }
  //
  //                          operation.getVendorExtensions.putAll(BaseReaderUtils.parseExtensions(apiOperation.extensions()))
  //                      }
  //                      if (operation.getConsumes == null) {
  //                          for (mediaType <- consumes) {
  //                              operation.consumes(mediaType)
  //                          }
  //                      }
  //                      if (operation.getProduces == null) {
  //                          for (mediaType <- produces) {
  //                              operation.produces(mediaType)
  //                          }
  //                      }
  //
  //                      if (operation.getTags == null) {
  //                          for (tagString <- tags.keys) {
  //                              operation.tag(tagString)
  //                          }
  //                      }
  //                      // Only add global @Api securities if operation doesn't already have more specific securities
  //                      if (operation.getSecurity == null) {
  //                          for (security <- securities) {
  //                              for(requirement <- security.getRequirements.asScala){
  //                                operation.addSecurity(requirement._1, requirement._2)
  //                              }
  //                          }
  //                      }

  private def getPathFromRoute(pathPattern: PathPattern, basePath: String): String = {
    val sb = new StringBuilder()
    val iter = pathPattern.parts.iterator
    while (iter.hasNext) {
      val part = iter.next
      part match {
        case staticPart: StaticPart => sb.append(staticPart.value)
        case dynamicPart: DynamicPart => {
          sb.append("{")
          sb.append(dynamicPart.name)
          sb.append("}")
        }
        case _ => logger.warn("ClassCastException parsing path from route {}", part.getClass.getSimpleName)
      }
    }
    val operationPath = new StringBuilder()
    val newBasePath =
      if (basePath.startsWith("/")) {
        basePath.substring(1)
      } else {
        basePath
      }
    operationPath.append(sb.toString.replaceFirst(newBasePath, ""))
    if (!operationPath.toString.startsWith("/")) {
      operationPath.insert(0, "/")
    }

    operationPath.toString
  }

  //  private def readSwaggerConfig(config: OpenAPIDefinition): Unit = {
  //      //if (!isEmpty(config.basePath())) {
  //      //    swagger.setBasePath(config.basePath())
  //      //}
  //
  //      if (!isEmpty(config.host())) {
  //          swagger.setHost(config.host())
  //      }
  //
  //      readInfoConfig(config)
  //
  //      for ( consume <- config.consumes()) {
  //          if (StringUtils.isNotEmpty(consume)) {
  //              swagger.addConsumes(consume)
  //          }
  //      }
  //
  //      for ( produce <- config.produces()) {
  //          if (StringUtils.isNotEmpty(produce)) {
  //              swagger.addProduces(produce)
  //          }
  //      }
  //
  //      if (!isEmpty(config.externalDocs.value())) {
  //          val externalDocs: ExternalDocs =
  //            if(swagger.getExternalDocs != null){
  //              swagger.getExternalDocs
  //            } else {
  //                val newExternalDocs = new ExternalDocs
  //                swagger.setExternalDocs(newExternalDocs)
  //                newExternalDocs
  //            }
  //
  //          externalDocs.setDescription(config.externalDocs.value)
  //
  //          if (!isEmpty(config.externalDocs.url)) {
  //              externalDocs.setUrl(config.externalDocs.url)
  //          }
  //      }
  //
  //      for (tagConfig <- config.tags()) {
  //          if (!isEmpty(tagConfig.name())) {
  //              val tag = new Tag()
  //              tag.setName(tagConfig.name)
  //              tag.setDescription(tagConfig.description)
  //
  //              if (!isEmpty(tagConfig.externalDocs.value)) {
  //                  tag.setExternalDocs(new ExternalDocs(tagConfig.externalDocs.value,
  //                                                       tagConfig.externalDocs.url))
  //              }
  //
  //              tag.getVendorExtensions.putAll(BaseReaderUtils.parseExtensions(tagConfig.extensions))
  //
  //              swagger.addTag(tag)
  //          }
  //      }
  //
  //      for (scheme <- config.schemes()) {
  //          if (scheme != SwaggerDefinition.Scheme.DEFAULT) {
  //              swagger.addScheme(Scheme.forValue(scheme.name()))
  //          }
  //      }
  //  }

  private def parseApiDefinition(annotation: io.swagger.v3.oas.annotations.OpenAPIDefinition): Unit = {
    api.setInfo(parseInfo(annotation.info))
    api.setTags(parseTags(annotation.tags).asJava)
    api.setExternalDocs(parseExternalDocumentation(annotation.externalDocs))
    api.setServers(parseServers(annotation.servers).asJava)
    api.setSecurity(parseSecurities(annotation.security).asJava)
    api.setExtensions(parseExtensions(annotation.extensions).asJava)
  }

  private def parseInfo(annotation: io.swagger.v3.oas.annotations.info.Info): Info = {
    val obj = new Info()
    obj.setTitle(annotation.title)
    obj.setDescription(annotation.description)
    obj.setVersion(annotation.version)
    obj.setTermsOfService(annotation.termsOfService)
    obj.setContact(parseContact(annotation.contact))
    obj.setLicense(parseLicense(annotation.license))
    obj.setExtensions(parseExtensions(annotation.extensions).asJava)
    obj
  }

  private def parseContact(annotation: io.swagger.v3.oas.annotations.info.Contact): Contact = {
    val obj = new Contact()
    obj.setName(annotation.name)
    obj.setUrl(annotation.url)
    obj.setEmail(annotation.email)
    obj.setExtensions(parseExtensions(annotation.extensions).asJava)
    obj
  }

  private def parseLicense(annotation: io.swagger.v3.oas.annotations.info.License): License = {
    val obj = new License()
    obj.setName(annotation.name)
    obj.setUrl(annotation.url)
    obj.setExtensions(parseExtensions(annotation.extensions).asJava)
    obj
  }

  private def parseTag(annotation: io.swagger.v3.oas.annotations.tags.Tag): Tag = {
    val obj = new Tag()
    obj.setName(annotation.name)
    obj.setDescription(annotation.description)
    obj.setExternalDocs(parseExternalDocumentation(annotation.externalDocs))
    obj.setExtensions(parseExtensions(annotation.extensions).asJava)
    obj
  }

  private def parseTags(annotations: Array[io.swagger.v3.oas.annotations.tags.Tag]): List[Tag] = annotations.toList.map(parseTag)

  private def parseExternalDocumentation(annotation: io.swagger.v3.oas.annotations.ExternalDocumentation): ExternalDocumentation = {
    val obj = new ExternalDocumentation()
    obj.setUrl(annotation.url)
    obj.setDescription(annotation.description)
    obj.setExtensions(parseExtensions(annotation.extensions).asJava)
    obj
  }

  private def parseServer(annotation: io.swagger.v3.oas.annotations.servers.Server): Server = {
    val obj = new Server()
    obj.setUrl(annotation.url)
    obj.setDescription(annotation.description)
    obj.setVariables(parseServerVariables(annotation.variables))
    obj.setExtensions(parseExtensions(annotation.extensions).asJava)
    obj
  }

  private def parseServers(annotations: Array[io.swagger.v3.oas.annotations.servers.Server]): List[Server] = annotations.toList.map(parseServer)

  private def parseServerVariables(annotations: Array[io.swagger.v3.oas.annotations.servers.ServerVariable]): ServerVariables = {
    val obj = new ServerVariables()
    for (annotation <- annotations) {
      val obj2 = new ServerVariable()
      obj2.setEnum(annotation.allowableValues.toList.asJava)
      obj2.setDescription(annotation.description)
      obj2.setDefault(annotation.defaultValue)
      obj2.extensions(parseExtensions(annotation.extensions).asJava)
      obj.addServerVariable(annotation.name, obj2)
    }
    obj
  }

  private def parseSecurityRequirement(annotation: io.swagger.v3.oas.annotations.security.SecurityRequirement): SecurityRequirement = {
    val obj = new SecurityRequirement()
    obj.addList(annotation.name, annotation.scopes.toList.asJava)
    obj
  }

  private def parseSecurities(annotations: Array[io.swagger.v3.oas.annotations.security.SecurityRequirement]): List[SecurityRequirement] =
    annotations.toList.map(parseSecurityRequirement)

  private def parseExtensions(annotations: Array[io.swagger.v3.oas.annotations.extensions.Extension]): Map[String, AnyRef] = {
    Map.empty[String, AnyRef]
  }

  /*
  private def readInfoConfig(config: SwaggerDefinition): Unit = {
      val infoConfig = config.info()
      val info: io.swagger.models.Info =
        if (swagger.getInfo != null) {
          swagger.getInfo
        } else {
            val newInfo = new io.swagger.models.Info()
            swagger.setInfo(newInfo)
            newInfo
        }

      if (!isEmpty(infoConfig.description)) {
          info.setDescription(infoConfig.description)
      }

      if (!isEmpty(infoConfig.termsOfService)) {
          info.setTermsOfService(infoConfig.termsOfService)
      }

      if (!isEmpty(infoConfig.title)) {
          info.setTitle(infoConfig.title)
      }

      if (!isEmpty(infoConfig.version)) {
          info.setVersion(infoConfig.version)
      }

      if (!isEmpty(infoConfig.contact.name)) {
          val contact: Contact =
            if(info.getContact != null){
              info.getContact
            } else {
              val newContact = new Contact()
              info.setContact(newContact)
              newContact
            }

          contact.setName(infoConfig.contact.name)
          if (!isEmpty(infoConfig.contact.email)) {
              contact.setEmail(infoConfig.contact.email)
          }

          if (!isEmpty(infoConfig.contact.url)) {
              contact.setUrl(infoConfig.contact.url)
          }
      }

      if (!isEmpty(infoConfig.license.name)) {
          val license: io.swagger.models.License =
            if(info.getLicense != null){
              info.getLicense
            } else {
              val newLicense = new io.swagger.models.License()
              info.setLicense(newLicense)
              newLicense
            }

          license.setName(infoConfig.license.name())
          if (!isEmpty(infoConfig.license.url())) {
              license.setUrl(infoConfig.license.url())
          }
      }

      info.getVendorExtensions.putAll(BaseReaderUtils.parseExtensions(infoConfig.extensions()))
  }*/

  //  private def readImplicitParameters(method: Method, operation: Operation, cls: Class[_]): Unit = {
  //      val implicitParams = method.getAnnotation(classOf[ApiImplicitParams])
  //      if (implicitParams != null && implicitParams.value.length > 0) {
  //          for (param <- implicitParams.value()) {
  //              val p = readImplicitParam(param, cls)
  //              if (p != null) {
  //                  operation.addParameter(p)
  //              }
  //          }
  //      }
  //  }

  //  private def readImplicitParam(param: ApiImplicitParam, cls: Class[_]): ApiParameter = {
  //      val p: Parameter =
  //        if (param.paramType.equalsIgnoreCase("path")) {
  //            new PathParameter()
  //        } else if (param.paramType.equalsIgnoreCase("query")) {
  //            new QueryParameter()
  //        } else if (param.paramType.equalsIgnoreCase("form") || param.paramType.equalsIgnoreCase("formData")) {
  //            new FormParameter()
  //        } else if (param.paramType.equalsIgnoreCase("body")) {
  //            null
  //        } else if (param.paramType.equalsIgnoreCase("header")) {
  //            new HeaderParameter()
  //        } else {
  //            logger.warn("Unkown implicit parameter type: [" + param.paramType() + "]")
  //            return null
  //        }
  //
  //      val t: Type =
  //      // Swagger ReflectionUtils can't handle file or array datatype
  //        if (!"".equalsIgnoreCase(param.dataType()) && !"file".equalsIgnoreCase(param.dataType()) && !"array".equalsIgnoreCase(param.dataType())) {
  //          typeFromString(param.dataType(), cls)
  //        } else {
  //          classOf[String]
  //        }
  //
  //      val result = ParameterProcessor.applyAnnotations(swagger, p, t, java.util.Collections.singletonList(param))
  //
  //      if (result.isInstanceOf[AbstractSerializableParameter[_]] && t != null) {
  //          val schema = createSchema(t)
  //          p.asInstanceOf[AbstractSerializableParameter[_]].setProperty(schema)
  //      }
  //
  //      result
  //  }

  private def typeFromString(t: String, cls: Class[_]): Type = {
    val primitive = PrimitiveType.fromName(t)
    if (primitive != null) {
      return primitive.getKeyClass
    }
    try {
      val routeType = getOptionTypeFromString(t, cls)

      if (routeType != null) {
        return routeType
      }

      return Thread.currentThread.getContextClassLoader.loadClass(t)
    } catch {
      case e: Exception => logger.error(s"Failed to resolve '$t' into class", e)
    }
    null
  }

  private def parseMethod(cls: Class[_], method: Method, route: Route, annotation: io.swagger.v3.oas.annotations.Operation): Operation = {
    val hidden = ReflectionUtils.getAnnotation(method, classOf[io.swagger.v3.oas.annotations.Hidden])
    if (hidden != null) {
      return null
    }

    val operation = new Operation()
    val responses = new ApiResponses()
    operation.setResponses(responses)
    operation.setOperationId(method.getName)

    if (annotation != null) {
      if (annotation.hidden()) {
        return null
      }

      if (!isEmpty(annotation.operationId)) {
        operation.setOperationId(annotation.operationId)
      }

      operation
        .summary(annotation.summary)
        .tags(annotation.tags.toList.asJava)
        .description(annotation.description)
        .externalDocs(parseExternalDocumentation(annotation.externalDocs))
        .deprecated(annotation.deprecated)
        .requestBody(parseRequestBody(annotation.requestBody))
        .security(parseSecurities(annotation.security).asJava)
        .extensions(parseExtensions(annotation.extensions).asJava)

      // Read parameters from annotation
      val parameters = parseParameters(annotation.parameters)
      parameters.foreach(operation.addParametersItem)
    }

    if (annotation == null || annotation.responses.isEmpty) {
      // pick out response from method declaration
      val responseType = method.getGenericReturnType

      if (!isResult(responseType) && isValidResponse(responseType)) {
        val schema = createSchema(responseType)
        if (schema != null) {
          val mediaType = new MediaType().schema(schema)
          val content = new Content().addMediaType(MEDIA_TYPE, mediaType)
          val response = new ApiResponse()
          response
            .description(SUCCESSFUL_OPERATION)
            .content(content)
          responses.setDefault(response)
        }
      }
    }

    // ApiResponses
    val responseAnnotations = ReflectionUtils.getAnnotation(method, classOf[io.swagger.v3.oas.annotations.responses.ApiResponses])
    if (responseAnnotations != null) {
      responses.putAll(parseResponses(responseAnnotations))
    }

    // List of ApiResponse
    val responseList = ReflectionUtils.getRepeatableAnnotations(method, classOf[io.swagger.v3.oas.annotations.responses.ApiResponse])
    if (responseList != null) {
      responses.putAll(parseResponses(responseList.asScala.toList))
    }

    if (ReflectionUtils.getAnnotation(method, classOf[Deprecated]) != null) {
      operation.setDeprecated(true)
    }

    val bodyAnnotation = ReflectionUtils.getAnnotation(method, classOf[io.swagger.v3.oas.annotations.parameters.RequestBody])
    if (bodyAnnotation != null) {
      operation.setRequestBody(parseRequestBody(bodyAnnotation))
    }

    val parametersAnnotation = ReflectionUtils.getAnnotation(method, classOf[io.swagger.v3.oas.annotations.Parameters])
    if (parametersAnnotation != null) {
      for(parameterAnnotation <- parametersAnnotation.value){
        operation.addParametersItem(parseParameter(parameterAnnotation))
      }
    }

    val parametersList = ReflectionUtils.getRepeatableAnnotations(method, classOf[io.swagger.v3.oas.annotations.Parameter])
    if (parametersList != null) {
      for(parameterAnnotation <- parametersList.asScala) {
        operation.addParametersItem(parseParameter(parameterAnnotation))
      }
    }

    // Pick parameter from route
    val routeParameters = getParameters(cls, method, route)
    routeParameters.foreach(operation.addParametersItem)

    if (responses.isEmpty) {
      val response = new ApiResponse()
      response.setDescription(SUCCESSFUL_OPERATION)
      responses.setDefault(response)
    }
    operation
  }

  private def parseParameter(annotation: io.swagger.v3.oas.annotations.Parameter): ApiParameter = {
    val obj = new ApiParameter()

    import io.swagger.v3.oas.annotations.enums.Explode
    annotation.explode match {
      case Explode.TRUE => obj.explode(true)
      case Explode.FALSE => obj.explode(false)
      case _ =>
    }

    if (AnnotationsUtils.hasSchemaAnnotation(annotation.array.schema)) {
      obj.setSchema(parseArraySchema(annotation.array))
    } else {
      obj.schema(parseSchema(annotation.schema))
    }

    obj
      .name(annotation.name)
      .in(annotation.in.toString)
      .description(annotation.description)
      .required(annotation.required)
      .deprecated(annotation.deprecated)
      .allowEmptyValue(annotation.allowEmptyValue)
      .allowReserved(annotation.allowReserved)
      .style(ApiParameter.StyleEnum.valueOf(annotation.style.toString))
      .examples(parseExamples(annotation.examples).asJava)
      .content(parseContents(annotation.content))
      .extensions(parseExtensions(annotation.extensions).asJava)
      .$ref(annotation.ref)
  }

  private def parseParameters(annotations: Array[io.swagger.v3.oas.annotations.Parameter]): List[ApiParameter] =
    annotations.toList.filter(!_.hidden).map(parseParameter)

  private def parseResponses(annotation: io.swagger.v3.oas.annotations.responses.ApiResponses): ApiResponses = parseResponses(annotation.value())

  private def parseResponses(annotations: Array[io.swagger.v3.oas.annotations.responses.ApiResponse]): ApiResponses = parseResponses(annotations.toList)

  private def parseResponses(annotations: List[io.swagger.v3.oas.annotations.responses.ApiResponse]): ApiResponses = {
    val obj = new ApiResponses()
    if (annotations.length == 1) {
      val response = parseResponse(annotations.head)
      obj.setDefault(response)
    } else {
      for (annotation <- annotations) {
        val response = parseResponse(annotation)
        obj.addApiResponse(annotation.responseCode, response)

        if ("default" == annotation.responseCode()) {
          obj.setDefault(response)
        }
      }
    }
    obj
  }

  private def parseResponse(annotation: io.swagger.v3.oas.annotations.responses.ApiResponse): ApiResponse = {
    val obj = new ApiResponse()
    obj.setLinks(parseLinks(annotation.links).asJava)
    obj
      .description(annotation.description)
      .headers(parseHeaders(annotation.headers).asJava)
      .content(parseContents(annotation.content))
      .extensions(parseExtensions(annotation.extensions).asJava)
      .$ref(annotation.ref)
  }

  private def parseHeader(annotation: io.swagger.v3.oas.annotations.headers.Header): Header = {
    val obj = new Header()
    obj
      .description(annotation.description)
      .deprecated(annotation.deprecated)
      .required(annotation.required)
      .schema(parseSchema(annotation.schema))
      .$ref(annotation.ref)
  }

  private def parseHeaders(annotations: Array[io.swagger.v3.oas.annotations.headers.Header]): Map[String, Header] =
    annotations.map(annotation => (annotation.name, parseHeader(annotation))).toMap

  private def parseRequestBody(annotation: io.swagger.v3.oas.annotations.parameters.RequestBody): RequestBody = {
    val obj = new RequestBody()
    obj
      .content(parseContents(annotation.content))
      .description(annotation.description)
      .required(annotation.required)
      .extensions(parseExtensions(annotation.extensions).asJava)
      .$ref(annotation.ref)
  }

  private def parseContents(annotations: Array[io.swagger.v3.oas.annotations.media.Content]): Content = {
    val obj = new Content()
    for (annotation <- annotations) {
      obj.addMediaType(annotation.mediaType, parseMediaType(annotation))
    }
    obj
  }

  private def parseMediaType(annotation: io.swagger.v3.oas.annotations.media.Content): MediaType = {
    val obj = new MediaType()

    if (AnnotationsUtils.hasSchemaAnnotation(annotation.array.schema)) {
      obj.setSchema(parseArraySchema(annotation.array))
    } else {
      obj.schema(parseSchema(annotation.schema))
    }

    obj
      .examples(parseExamples(annotation.examples).asJava)
      .encoding(parseEncodings(annotation.encoding).asJava)
      .extensions(parseExtensions(annotation.extensions).asJava)
  }

  private def parseLink(annotation: io.swagger.v3.oas.annotations.links.Link): Link = {
    val obj = new Link()
    obj.setParameters(parseLinkParameters(annotation.parameters).asJava)
    obj
      .server(parseServer(annotation.server))
      .operationRef(annotation.operationRef)
      .operationId(annotation.operationId)
      .description(annotation.description)
      .extensions(parseExtensions(annotation.extensions).asJava)
      .$ref(annotation.ref)

    // FIXME requestbody
  }

  private def parseLinks(annotations: Array[io.swagger.v3.oas.annotations.links.Link]): Map[String, Link] =
    annotations.map(annotation => (annotation.name, parseLink(annotation))).toMap

  // FIXME Link expression
  private def parseLinkParameters(annotations: Array[io.swagger.v3.oas.annotations.links.LinkParameter]): Map[String, String] =
    annotations.map(annotation => (annotation.name, annotation.expression)).toMap

  private def parseExample(annotation: io.swagger.v3.oas.annotations.media.ExampleObject): Example = {
    val obj = new Example()
    obj
      .value(annotation.value)
      .externalValue(annotation.externalValue)
      .summary(annotation.summary)
      .description(annotation.description)
      .$ref(annotation.ref)
      .extensions(parseExtensions(annotation.extensions).asJava)
  }

  private def parseExamples(annotations: Array[io.swagger.v3.oas.annotations.media.ExampleObject]): Map[String, Example] =
    annotations.map(annotation => (annotation.name, parseExample(annotation))).toMap

  private def parseEncoding(annotation: io.swagger.v3.oas.annotations.media.Encoding): Encoding = {
    val obj = new Encoding()
    obj
      .contentType(annotation.contentType)
      .style(Encoding.StyleEnum.valueOf(annotation.style))
      .explode(annotation.explode)
      .allowReserved(annotation.allowReserved)
      .headers(parseHeaders(annotation.headers).asJava)
      .extensions(parseExtensions(annotation.extensions).asJava)
  }

  private def parseEncodings(annotations: Array[io.swagger.v3.oas.annotations.media.Encoding]): Map[String, Encoding] =
    annotations.map(annotation => (annotation.name, parseEncoding(annotation))).toMap

  private def parseSchema(annotation: io.swagger.v3.oas.annotations.media.Schema): Schema[_] = {
    val schema =
      if (isVoid(annotation.implementation)) {
        new Schema
      } else {
        createSchema(annotation.implementation)
      }

    import java.math.BigDecimal
    import io.swagger.v3.oas.annotations.media.Schema.AccessMode
    annotation.accessMode match {
      case AccessMode.READ_ONLY => schema.setReadOnly(true)
      case AccessMode.WRITE_ONLY => schema.setWriteOnly(true)
      case AccessMode.READ_WRITE | AccessMode.AUTO  => {
        schema.setReadOnly(true)
        schema.setWriteOnly(true)
      }
    }

    // FIXME Enum

    schema
      .name(annotation.name)
      .title(annotation.title)
      .description(annotation.description)
      .multipleOf(new BigDecimal(annotation.multipleOf()))
      .maximum(new BigDecimal(annotation.maximum))
      .exclusiveMaximum(annotation.exclusiveMaximum)
      .minimum(new BigDecimal(annotation.minimum))
      .exclusiveMinimum(annotation.exclusiveMinimum)
      .maxLength(annotation.maxLength)
      .minLength(annotation.minLength)
      .pattern(annotation.pattern)
      .minProperties(annotation.minProperties)
      .required(annotation.requiredProperties.toList.asJava)
      .format(annotation.format)
      .nullable(annotation.nullable)
      .deprecated(annotation.deprecated)
      .`type`(annotation.`type`)
      .externalDocs(parseExternalDocumentation(annotation.externalDocs))
      .extensions(parseExtensions(annotation.extensions).asJava)
      .$ref(annotation.ref)

    api.schema(schema.getName, schema)
    schema
  }

  private def parseArraySchema(annotation: io.swagger.v3.oas.annotations.media.ArraySchema): ArraySchema = {
    val obj = new ArraySchema()
    if (AnnotationsUtils.hasSchemaAnnotation(annotation.schema)){
      obj.items(parseSchema(annotation.schema))
    }

    if (AnnotationsUtils.hasSchemaAnnotation(annotation.arraySchema) &&
      annotation.arraySchema.isInstanceOf[io.swagger.v3.oas.annotations.media.ArraySchema]){
      val arraySchema = annotation.arraySchema.asInstanceOf[io.swagger.v3.oas.annotations.media.ArraySchema]
      obj.items(parseArraySchema(arraySchema))
    }

    obj
      .minItems(annotation.minItems)
      .maxItems(annotation.maxItems)
      .uniqueItems(annotation.uniqueItems())
    obj
  }

  private val primitiveTypes: Map[String, Class[_]] = Map(
    "Int" -> classOf[java.lang.Integer],
    "Long" -> classOf[java.lang.Long],
    "Byte" -> classOf[java.lang.Byte],
    "Boolean" -> classOf[java.lang.Boolean],
    "Char" -> classOf[java.lang.Character],
    "Float" -> classOf[java.lang.Float],
    "Double" -> classOf[java.lang.Double],
    "Short" -> classOf[java.lang.Short],
  )

  private def getOptionTypeFromString(simpleTypeName: String, cls: Class[_]): Type = {
    if (simpleTypeName == null) {
      return null
    }
    val regex = "(Option|scala\\.Option)\\s*\\[\\s*(Int|Long|Float|Double|Byte|Short|Char|Boolean)\\s*\\]\\s*$"

    val pattern = Pattern.compile(regex)
    val matcher = pattern.matcher(simpleTypeName)
    if (matcher.find()) {
      val enhancedType = matcher.group(2)
      val parameterType = PrimitiveType.fromName(enhancedType)
      typeFactory.constructType(parameterType.getKeyClass)
    } else {
      null
    }
  }

  private def getParamType(cls: Class[_], method: Method, simpleTypeName: String, position: Int): Type = {
    try {
      val t = getOptionTypeFromString(simpleTypeName, cls)
      if (t != null) {
        return t
      }

      val genericParameterTypes = method.getGenericParameterTypes
      val parameterType = genericParameterTypes(position)
      typeFactory.constructType(parameterType)
    } catch {
      case e: Exception => {
        logger.error(s"Exception getting parameter type for method $method, param $simpleTypeName at position $position", e)
        null
      }
    }
  }

  private def getParamAnnotations(cls: Class[_], genericParameterTypes: Array[Type], paramAnnotations: Array[Array[Annotation]], simpleTypeName: String, fieldPosition: Int): List[Annotation] = {
    try {
      paramAnnotations(fieldPosition).toList
    } catch {
      case e: Exception => {
        logger.error(s"Exception getting parameter type for $simpleTypeName at position $fieldPosition", e)
        List.empty
      }
    }
  }

  private def getParamAnnotations(cls: Class[_], method: Method, simpleTypeName: String, fieldPosition: Int): List[Annotation] = {
    val genericParameterTypes = method.getGenericParameterTypes
    val paramAnnotations = method.getParameterAnnotations
    val annotations = getParamAnnotations(cls, genericParameterTypes, paramAnnotations, simpleTypeName, fieldPosition)
    if (annotations.nonEmpty) {
      return annotations
    }

    // Fallback to type
    for (i <- 0 until genericParameterTypes.length) {
      val annotations2 = getParamAnnotations(cls, genericParameterTypes, paramAnnotations, simpleTypeName, i)
      if (annotations2.nonEmpty) {
        return annotations2
      }
    }

    List.empty
  }

  private def getParameters(cls: Class[_], method: Method, route: Route): List[ApiParameter] = {
    // TODO now consider only parameters defined in route, excluding body parameters
    // understand how to possibly infer body/form params e.g. from @BodyParser or other annotation

    if (route.call.parameters.isEmpty) {
      return List.empty
    }

    val parameters = ListBuffer.empty[ApiParameter]

    for ((p, fieldPosition) <- route.call.parameters.get.zipWithIndex) {
      if (p.fixed.isEmpty) {
        var defaultField = CrossUtil.getParameterDefaultField(p)
        if (defaultField.startsWith("\"") && defaultField.endsWith("\"")) {
          defaultField = defaultField.substring(1, defaultField.length() - 1)
        }
        val t = getParamType(cls, method, p.typeName, fieldPosition)

        // Ignore play.mvc.Http.Request
        if (!t.getTypeName.equals("[simple type, class play.mvc.Http$Request]")) {
          val schema = createSchema(t)
          schema.setDefault(defaultField)

          val parameter: ApiParameter =
            if (route.path.has(p.name)) {
              // it's a path param
              val pathParameter = new PathParameter()
              if (schema != null) {
                pathParameter.setSchema(schema)
              }
              pathParameter
            } else {
              // it's a query string param
              val queryParameter = new QueryParameter()
              if (schema != null) {
                queryParameter.setSchema(schema)
              }
              queryParameter
            }
          parameter.setName(p.name)
          val annotations = getParamAnnotations(cls, method, p.typeName, fieldPosition)
          ParameterProcessor.applyAnnotations(parameter, t, annotations.asJava, api.getComponents,
            new Array[String](0), new Array[String](0), null)
          parameters += parameter
        }
      }
    }
    parameters.toList
  }

  //  private def parseSchemes(schemes: String): Set[Scheme] = {
  //      val result = scala.collection.mutable.Set.empty[Scheme]
  //      for (item <- trimToEmpty(schemes).split(",")) {
  //          val scheme = Scheme.forValue(trimToNull(item))
  //          if (scheme != null) {
  //              result += scheme
  //          }
  //      }
  //      result.toSet
  //  }

  private def isVoid(t: Type): Boolean = {
    val cls = typeFactory.constructType(t).getRawClass
    classOf[Void].isAssignableFrom(cls) || Void.TYPE.isAssignableFrom(cls)
  }

  // Play Result
  private def isResult(t: Type): Boolean = {
    val cls = typeFactory.constructType(t).getRawClass
    classOf[play.api.mvc.Result].isAssignableFrom(cls) || classOf[play.mvc.Result].isAssignableFrom(cls)
  }

  private def isValidResponse(t: Type): Boolean = {
    if (t == null) {
      return false
    }
    val javaType = typeFactory.constructType(t)
    if (isVoid(javaType)) {
      return false
    }
    val cls = javaType.getRawClass
    !isResourceClass(cls)
  }

  private def isResourceClass(cls: Class[_]) = cls.getAnnotation(classOf[io.swagger.v3.oas.annotations.media.Schema]) != null

  /*
  private def extractTags(api: Api): Set[String] = {
      val output = scala.collection.mutable.Set.empty[String]

      var hasExplicitTags = false
      for (tag <- api.tags()) {
          if (!tag.isEmpty) {
              hasExplicitTags = true
              output.add(tag)
          }
      }
      if (!hasExplicitTags) {
          // derive tag from api path + description
          val tagString = api.value.replace("/", "")
          if (!tagString.isEmpty) {
              output.add(tagString)
          }
      }
      output.toSet
  }*/

  private def createSchema(t: Type): Schema[_] = {
    val resolvedSchema = modelConverters.readAllAsResolvedSchema(t)
    if (resolvedSchema == null) {
      return null
    }

    //enforcePrimitive(schema.schema, 0)
    val schema = resolvedSchema.schema
    api.schema(schema.getName, schema)

    //for ((modelName, model) <- resolvedSchema.referencedSchemas.asScala) {
    //   api.schema(modelName, model)
    //}
    schema
  }

  private def enforcePrimitive(in: Schema[_], level: Int): Schema[_] = {
    // FIXME
    //if (in.isInstanceOf[RefProperty]) {
    //  return new StringSchema()
    //}
    if (in.isInstanceOf[ArraySchema]) {
      if (level == 0) {
        val array = in.asInstanceOf[ArraySchema]
        array.setItems(enforcePrimitive(array.getItems, level + 1))
      } else {
        return new StringSchema()
      }
    }
    in
  }

//  private def appendModels(t: Type): Unit = {
//    val models = modelConverters.readAll(t)
//    for ((modelName, model) <- models.asScala) {
//      api.schema(modelName, model)
//    }
//  }

  //  private def parseResponseHeaders(headers: Array[io.swagger.v3.oas.annotations.headers.Header]): Map[String, Schema[_]] = {
  //      val responseHeaders = scala.collection.mutable.Map.empty[String, Schema[_]]
  //      if (headers != null && headers.length > 0) {
  //          for (header <- headers) {
  //              val name = header.name()
  //              if (!isEmpty(name)) {
  //                  val description = header.description()
  //                  val cls = header.response()
  //
  //                  if (!isVoid(cls)) {
  //                      val property = modelConverters.readAllAsResolvedSchema(cls)
  //                      if (property != null) {
  //                          val responseProperty = wrapContainer(header.responseContainer(), property,
  //                                                               ContainerWrapper.ARRAY,
  //                                                               ContainerWrapper.LIST,
  //                                                               ContainerWrapper.SET)
  //                          responseProperty.setDescription(description)
  //                          responseHeaders.put(name, responseProperty)
  //                          appendModels(cls)
  //                      }
  //                  }
  //              }
  //          }
  //      }
  //      responseHeaders.toMap
  //  }

  private def getFullMethodName(clazz: Class[_], method: Method): String = {
    if (!clazz.getCanonicalName.contains("$")) {
      clazz.getCanonicalName + "$." + method.getName
    } else {
      clazz.getCanonicalName + "." + method.getName
    }
  }

  private def extractOperationMethod(apiOperation: ApiOperation, method: Method, route: Route): PathItem.HttpMethod = {
    if (route != null) {
      try {
        return PathItem.HttpMethod.valueOf(route.verb.toString.toUpperCase())
      } catch {
        case e: Exception => logger.error(s"http method not found for method: ${method.getName}", e)
      }
    }
    if (!isEmpty(apiOperation.method)) {
      return PathItem.HttpMethod.valueOf(apiOperation.method.toUpperCase())
    }
    null
  }

  private def toArray(csString: String): Array[String] = {
    if (isEmpty(csString)) {
      return Array(csString)
    }
    var i = 0
    val result = csString.split(",")
    for (c <- result) {
      result(i) = c.trim()
      i += 1
    }
    result
  }

  private object ContainerWrapper {
    type Wrapper = (String, Schema[_] => Schema[_])

    val LIST: Wrapper = ("list", wrapList)
    val ARRAY: Wrapper = ("array", wrapList)
    val MAP: Wrapper = ("map", wrapMap)
    val SET: Wrapper = ("set", wrapSet)

    val ALL = List(LIST, ARRAY, MAP, SET)

    def wrapList(schema: Schema[_]): Schema[_] = new ArraySchema().items(schema)

    def wrapMap(schema: Schema[_]): Schema[_] = {
      val schema = new MapSchema()
      schema
    }

    def wrapSet(schema: Schema[_]): Schema[_] = {
      new ArraySchema()
        .items(schema)
        .uniqueItems(true)
    }
  }

  private def wrapContainer(container: String, property: Schema[_], allowed: ContainerWrapper.Wrapper*): Schema[_] = {
    val wrappers =
      if (allowed.isEmpty) {
        ContainerWrapper.ALL
      } else {
        allowed
      }

    for (wrapper <- wrappers) {
      if (wrapper._1.equalsIgnoreCase(container)) {
        return wrapper._2.apply(property)
      }
    }
    property
  }
}
