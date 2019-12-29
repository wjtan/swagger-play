package play.modules.swagger

import io.swagger.v3.oas.annotations.{Operation => ApiOperation}
import io.swagger.v3.core.converter.{AnnotatedType, ModelConverters}
import io.swagger.v3.core.util.AnnotationsUtils
import io.swagger.v3.jaxrs2.{OperationParser, SecurityParser}
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
import io.swagger.v3.core.util._
import org.apache.commons.lang3.StringUtils
import com.typesafe.scalalogging._
import play.modules.swagger.util.CrossUtil
import play.modules.swagger.util.JavaOptionals._
import play.routes.compiler._

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._
import java.lang.annotation.Annotation
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.util.regex.Pattern

import javax.inject.Inject
import javax.inject.Singleton

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

  private[this] val components = new Components()
  api.setComponents(components)

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

    // Class Response

    // SecurityScheme

    // SecurityRequirement

    // ExternalDocs

    // Tags

    // Servers

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
      if (api.getPaths != null && api.getPaths.containsKey(operationPath)) {
        api.getPaths.get(operationPath)
      } else {
        val path = new PathItem()
        api.path(operationPath, path)
        path
      }

    path.operation(httpMethod, operation)
  }

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
    AnnotationsUtils.getInfo(annotation.info).toOption.foreach(api.setInfo)
    SecurityParser.getSecurityRequirements(annotation.security).toOption.foreach(api.setSecurity)
    AnnotationsUtils.getExternalDocumentation(annotation.externalDocs).toOption.foreach(api.setExternalDocs)

    // FIXME Tags
    // AnnotationsUtils.getTags(annotation.tags(), false).toOption.foreach(tags.addAll)

    AnnotationsUtils.getServers(annotation.servers).toOption.foreach(api.setServers)
    if (annotation.extensions.length > 0) {
      api.setExtensions(AnnotationsUtils.getExtensions(annotation.extensions():_*))
    }
  }

  private def parseOAuthFlows(annotation: io.swagger.v3.oas.annotations.security.OAuthFlows): Option[OAuthFlows] =
    SecurityParser.getOAuthFlows(annotation).toOption

  private def parseOAuthFlow(annotation: io.swagger.v3.oas.annotations.security.OAuthFlow): Option[OAuthFlow] =
    SecurityParser.getOAuthFlow(annotation).toOption

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
        .deprecated(annotation.deprecated)

      // Responses
      if (!annotation.responses.isEmpty) {
        OperationParser.getApiResponses(annotation.responses, null, null, components, null)
          .toOption
          .foreach(responses.putAll)
      }

      OperationParser.getRequestBody(annotation.requestBody, null, null, components, null)
        .toOption
        .foreach(operation.setRequestBody)

      AnnotationsUtils.getExternalDocumentation(annotation.externalDocs).toOption.foreach(operation.setExternalDocs)
      SecurityParser.getSecurityRequirements(annotation.security).toOption.foreach(operation.setSecurity)

      if (annotation.extensions.length > 0) {
        AnnotationsUtils.getExtensions(annotation.extensions():_*).asScala.foreachEntry((k,v) => operation.addExtension(k, v))
      }

      // Read parameters from annotation
      for(parameterAnnotation <- annotation.parameters){
        parseParameter(parameterAnnotation).foreach(operation.addParametersItem)
      }
    }

    if (annotation == null || annotation.responses.isEmpty) {
      // pick out response from method declaration
      val responseType = method.getGenericReturnType

      if (!isResult(responseType) && isValidResponse(responseType)) {
        createSchema(responseType)
          .foreach(schema => {
            val mediaType = new MediaType().schema(schema)
            val content = new Content().addMediaType(MEDIA_TYPE, mediaType)
            val response = new ApiResponse()
            response
              .description(SUCCESSFUL_OPERATION)
              .content(content)
            responses.setDefault(response)
          })
      }
    }

    // Security

    // Callbacks

    // Servers

    // Tags

    // Parameters
    val routeParameters = getParameters(cls, method, route)
    routeParameters.foreach(operation.addParametersItem)

    val parametersList = ReflectionUtils.getRepeatableAnnotations(method, classOf[io.swagger.v3.oas.annotations.Parameter])
    if (parametersList != null) {
      for(parameterAnnotation <- parametersList.asScala) {
        parseParameter(parameterAnnotation).foreach(operation.addParametersItem)
      }
    }

    // ApiResponses
    val responseList = ReflectionUtils.getRepeatableAnnotations(method, classOf[io.swagger.v3.oas.annotations.responses.ApiResponse])
    if (responseList != null && !responseList.isEmpty) {
      val array = responseList.asScala.toArray[io.swagger.v3.oas.annotations.responses.ApiResponse]
      OperationParser.getApiResponses(array, null, null, components, null)
        .toOption
        .foreach(responses.putAll)
    }

    if (ReflectionUtils.getAnnotation(method, classOf[Deprecated]) != null) {
      operation.setDeprecated(true)
    }

    // Request Body
    val bodyAnnotation = ReflectionUtils.getAnnotation(method, classOf[io.swagger.v3.oas.annotations.parameters.RequestBody])
    if (bodyAnnotation != null) {
      OperationParser.getRequestBody(bodyAnnotation, null, null, components, null)
          .toOption
          .foreach(operation.setRequestBody)
    }

    // ExternalDocumentation

    if (responses.isEmpty) {
      val response = new ApiResponse()
      response.setDescription(SUCCESSFUL_OPERATION)
      responses.setDefault(response)
    }
    operation
  }

  private def parseParameter(annotation: io.swagger.v3.oas.annotations.Parameter): Option[ApiParameter] = {
    val parameterType = ParameterProcessor.getParameterType(annotation)
    val processedParameter = ParameterProcessor.applyAnnotations(
      null,
      parameterType,
      List(annotation.asInstanceOf[Annotation]).asJava,
      components,
      new Array[String](0),
      new Array[String](0),
      null)

    Option.apply(processedParameter)
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
          schema.foreach(_.setDefault(defaultField))

          val parameter: ApiParameter =
            if (route.path.has(p.name)) {
              // it's a path param
              val pathParameter = new PathParameter()
              schema.foreach(pathParameter.setSchema)
              pathParameter
            } else {
              // it's a query string param
              val queryParameter = new QueryParameter()
              schema.foreach(queryParameter.setSchema)
              queryParameter
            }
          parameter.setName(p.name)
          val annotations = getParamAnnotations(cls, method, p.typeName, fieldPosition)
          ParameterProcessor.applyAnnotations(parameter, t, annotations.asJava, components,
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

  private def createSchema(t: Type): Option[Schema[_]] = {
    val resolvedSchema = modelConverters.readAllAsResolvedSchema(new AnnotatedType().`type`(t))
    if (resolvedSchema == null) {
      return Option.empty
    }

    for ((modelName, model) <- resolvedSchema.referencedSchemas.asScala) {
      components.addSchemas(modelName, model)
    }

    if (resolvedSchema.schema != null) {
      if (isNotBlank(resolvedSchema.schema.getName)){
        val schema = new Schema()
        schema.set$ref(AnnotationsUtils.COMPONENTS_REF + resolvedSchema.schema.getName)
        Option(schema)
      } else {
        Option(resolvedSchema.schema)
      }
    } else {
      Option.empty
    }
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
}
