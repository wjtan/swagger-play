package play.modules.swagger

import java.lang.annotation.Annotation
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.util
import java.util.regex.Pattern

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._

import org.apache.commons.lang3.StringUtils
import com.typesafe.scalalogging._
import io.swagger.v3.oas.annotations.{Operation => ApiOperation}
import io.swagger.v3.core.converter.{AnnotatedType, ModelConverters}
import io.swagger.v3.core.util.AnnotationsUtils
import io.swagger.v3.jaxrs2.{OperationParser, SecurityParser}
import io.swagger.v3.oas.models._
import io.swagger.v3.oas.models.tags.Tag
import io.swagger.v3.oas.models.media._
import io.swagger.v3.oas.models.parameters._
import io.swagger.v3.oas.models.parameters.{Parameter => ApiParameter}
import io.swagger.v3.oas.models.responses._
import io.swagger.v3.oas.models.security._
import io.swagger.v3.oas.models.servers._
import io.swagger.v3.core.util._
import io.swagger.v3.oas.integration._
import io.swagger.v3.oas.integration.api._
import io.swagger.v3.oas.models.callbacks.Callback
import play.modules.swagger.util.CrossUtil
import play.modules.swagger.util.JavaOptionals._
import play.routes.compiler._

class PlayReader(routes: RouteWrapper) extends OpenApiReader {
  private[this] val logger = Logger[PlayReader]

  private[this] val typeFactory = Json.mapper.getTypeFactory
  private[this] val modelConverters = ModelConverters.getInstance

  val SUCCESSFUL_OPERATION = "successful operation"
  val MEDIA_TYPE = "application/json"

  private[this] var api: OpenAPI = new OpenAPI()
  private[this] var paths = new Paths()
  private[this] var components = new Components()
  private[this] var config: OpenAPIConfiguration = new SwaggerConfiguration().openAPI(api)
  private[this] val tags = collection.mutable.Set.empty[Tag]
  api.setComponents(components)
  api.setPaths(paths)

  import StringUtils._

  override def setConfiguration(openApiConfiguration: OpenAPIConfiguration): Unit = {
    if (openApiConfiguration != null) {
      config = ContextUtils.deepCopy(openApiConfiguration)
      if (openApiConfiguration.getOpenAPI != null) {
        api = config.getOpenAPI
        if (api.getComponents != null) {
          components = api.getComponents
        } else {
          api.setComponents(components)
        }

        if (api.getPaths != null) {
          paths = api.getPaths
        } else {
          api.setPaths(paths)
        }
      }
    }
  }

  override def read(classes: util.Set[Class[_]], resources: util.Map[String, AnyRef]): OpenAPI = read(classes.asScala.toList)

  def read(classes: List[Class[_]]): OpenAPI = {
    // process SwaggerDefinitions first - so we get tags in desired order
    for (cls <- classes) {
      val definition = ReflectionUtils.getAnnotation(cls, classOf[io.swagger.v3.oas.annotations.OpenAPIDefinition])
      if (definition != null) {
        parseApiDefinition(definition)
      }
    }

    implicit val apiServers: List[Server] =
      Option(api.getServers)
        .map(_.asScala.toList)
        .getOrElse(List.empty[Server])

    implicit val apiSecurity: List[SecurityRequirement] =
      Option(api.getSecurity)
        .map(_.asScala.toList)
        .getOrElse(List.empty[SecurityRequirement])

    implicit val apiDocs: Option[ExternalDocumentation] = Option(api.getExternalDocs)

    for (cls <- classes) {
      readClass(cls)
    }

    api.setTags(tags.toList.asJava)
    api
  }

  def readClass(cls: Class[_])
               (implicit apiServers: List[Server],
                apiSecurity: List[SecurityRequirement],
                apiDocs: Option[ExternalDocumentation]): Unit = {
    val hidden = ReflectionUtils.getAnnotation(cls, classOf[io.swagger.v3.oas.annotations.Hidden])

    if (hidden != null) {
      return
    }

    // Response
    val responses = ReflectionUtils.getRepeatableAnnotationsArray(cls, classOf[io.swagger.v3.oas.annotations.responses.ApiResponse])
    val classResponses = Option(responses).getOrElse(new Array[io.swagger.v3.oas.annotations.responses.ApiResponse](0))

    // SecurityScheme
    val securitySchemeAnnotations = ReflectionUtils.getRepeatableAnnotationsArray(cls, classOf[io.swagger.v3.oas.annotations.security.SecurityScheme])
    if (securitySchemeAnnotations != null) {
      val securityMap = collection.mutable.Map.empty[String, SecurityScheme]
      for(securitySchemeAnnotation <- securitySchemeAnnotations) {
        SecurityParser.getSecurityScheme(securitySchemeAnnotation)
          .toOption
          .foreach(pair => {
            if (isNotBlank(pair.key)) {
              securityMap += pair.key -> pair.securityScheme
            }
          })
      }
      components.setSecuritySchemes(securityMap.asJava)
    }

    // SecurityRequirement
    val securityRequirementAnnotations = ReflectionUtils.getRepeatableAnnotationsArray(cls, classOf[io.swagger.v3.oas.annotations.security.SecurityRequirement])
    val classSecurityRequirements: List[SecurityRequirement] = apiSecurity ++ {
      if (securityRequirementAnnotations != null && securityRequirementAnnotations.nonEmpty) {
        SecurityParser.getSecurityRequirements(securityRequirementAnnotations)
          .toOption
          .map(_.asScala.toList)
          .getOrElse(List.empty[SecurityRequirement])
      } else List.empty[SecurityRequirement]
    }

    // ExternalDocs
    val docAnnotation = ReflectionUtils.getAnnotation(cls, classOf[io.swagger.v3.oas.annotations.ExternalDocumentation])
    val classDocs: Option[ExternalDocumentation] = Option(docAnnotation).flatMap(annotation => {
      AnnotationsUtils.getExternalDocumentation(annotation).toOption
    }) orElse apiDocs

    // Tags
    val tagAnnotations = ReflectionUtils.getRepeatableAnnotationsArray(cls, classOf[io.swagger.v3.oas.annotations.tags.Tag])
    val classTags: Set[Tag] =
      if (tagAnnotations != null && tagAnnotations.nonEmpty) {
        AnnotationsUtils.getTags(tagAnnotations, false)
          .toOption
          .map(_.asScala.toSet)
          .getOrElse(Set.empty[Tag])
      } else {
        Set.empty[Tag]
      }

    tags ++= classTags

    // Servers
    val serverAnnotations = ReflectionUtils.getRepeatableAnnotationsArray(cls, classOf[io.swagger.v3.oas.annotations.servers.Server])
    val classServers: List[Server] = apiServers ++ {
      if (serverAnnotations != null) {
        AnnotationsUtils.getServers(serverAnnotations)
          .toOption
          .map(_.asScala.toList)
          .getOrElse(List.empty[Server])
      } else List.empty[Server]
    }

    // parse the method
    val methods = cls.getMethods
    for (method <- methods) {
      readMethod(cls, method)(classServers, classTags, classResponses, classSecurityRequirements, classDocs)
    }
  }

  private def readMethod(cls: Class[_], method: Method)
                        (implicit classServers: List[Server],
                                  classTags: Set[Tag],
                                  classResponses: Array[io.swagger.v3.oas.annotations.responses.ApiResponse],
                                  classSecurityRequirements: List[SecurityRequirement],
                                  classDocs: Option[ExternalDocumentation]): Unit = {
    if (ReflectionUtils.isOverriddenMethod(method, cls)) return

    // complete name as stored in route
    val fullMethodName = getFullMethodName(cls, method)

    if (!routes.exists(fullMethodName)) return

    val route = routes(fullMethodName)

    val basePath =
      if (classServers.isEmpty) {
        "/"
      } else {
        classServers.head.getUrl
      }

    val operationPath = getPathFromRoute(route.path, basePath)
    if (operationPath == null) return

    val apiOperation = ReflectionUtils.getAnnotation(method, classOf[io.swagger.v3.oas.annotations.Operation])
    if (apiOperation == null) return

    val httpMethod = extractOperationMethod(apiOperation, method, route)
    if (httpMethod == null) return

    val operation = parseMethod(cls, method, route, apiOperation)

    val path: PathItem =
      if (paths.containsKey(operationPath)) {
        paths.get(operationPath)
      } else {
        val path = new PathItem()
        paths.addPathItem(operationPath, path)
        path
      }

    operation.foreach(path.operation(httpMethod, _))
  }

  private def getPathFromRoute(pathPattern: PathPattern, basePath: String): String = {
    val sb = new StringBuilder()
    val iter = pathPattern.parts.iterator
    while (iter.hasNext) {
      val part = iter.next
      part match {
        case staticPart: StaticPart => sb ++= staticPart.value
        case dynamicPart: DynamicPart => sb ++= "{" ++ dynamicPart.name ++ "}"
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

  private def parseApiDefinition(annotation: io.swagger.v3.oas.annotations.OpenAPIDefinition): Unit = {
    AnnotationsUtils.getInfo(annotation.info).toOption.foreach(api.setInfo)
    SecurityParser.getSecurityRequirements(annotation.security).toOption.foreach(api.setSecurity)
    AnnotationsUtils.getExternalDocumentation(annotation.externalDocs).toOption.foreach(api.setExternalDocs)

    AnnotationsUtils.getTags(annotation.tags(), false).toOption.foreach(tags => this.tags ++= tags.asScala)

    AnnotationsUtils.getServers(annotation.servers).toOption.foreach(api.setServers)
    if (annotation.extensions.length > 0) {
      api.setExtensions(AnnotationsUtils.getExtensions(annotation.extensions():_*))
    }
  }

  private def parseMethod(cls: Class[_], method: Method, route: Route, annotation: io.swagger.v3.oas.annotations.Operation)
                         (implicit classServers: List[Server],
                                   classTags: Set[Tag],
                                   classResponses: Array[io.swagger.v3.oas.annotations.responses.ApiResponse],
                                   classSecurityRequirements: List[SecurityRequirement],
                                   classDocs: Option[ExternalDocumentation]): Option[Operation] = {
    val hidden = ReflectionUtils.getAnnotation(method, classOf[io.swagger.v3.oas.annotations.Hidden])
    if (hidden != null) {
      return Option.empty
    }

    val operation = new Operation()
    val responses = new ApiResponses()
    operation.setResponses(responses)
    operation.setOperationId(method.getName)

    if (annotation != null) {
      if (annotation.hidden()) {
        return Option.empty
      }

      if (!isEmpty(annotation.operationId)) {
        operation.setOperationId(annotation.operationId)
      }

      operation
        .summary(annotation.summary)
        .description(annotation.description)
        .deprecated(annotation.deprecated)
    }

    if (ReflectionUtils.getAnnotation(method, classOf[Deprecated]) != null) {
      operation.setDeprecated(true)
    }

    // Responses
    val responseAnnotations = collection.mutable.ArrayBuffer.empty[io.swagger.v3.oas.annotations.responses.ApiResponse]
    responseAnnotations ++=  classResponses
    if (annotation != null) {
      responseAnnotations ++= annotation.responses
    }

    val responseAnnotations2 = ReflectionUtils.getRepeatableAnnotations(method, classOf[io.swagger.v3.oas.annotations.responses.ApiResponse])
    if (responseAnnotations2 != null){
      responseAnnotations ++= responseAnnotations2.asScala
    }

    if (responseAnnotations.nonEmpty) {
      val array = responseAnnotations.toArray[io.swagger.v3.oas.annotations.responses.ApiResponse]
      OperationParser.getApiResponses(array, null, null, components, null)
        .toOption
        .foreach(responses.putAll)
    } else {
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
    classSecurityRequirements.foreach(operation.addSecurityItem)

    if (annotation != null) {
      SecurityParser.getSecurityRequirements(annotation.security)
        .toOption
        .foreach(list => list.asScala.foreach(operation.addSecurityItem))
    }

    // Callbacks
    val callbackAnnotations = ReflectionUtils.getRepeatableAnnotations(method, classOf[io.swagger.v3.oas.annotations.callbacks.Callback])
    if (callbackAnnotations != null) {
      val callbacks = callbackAnnotations.asScala.map(parseCallback).toMap
      if (callbacks.nonEmpty) {
        operation.setCallbacks(callbacks.asJava)
      }
    }

    // Servers
    classServers.foreach(operation.addServersItem)

    // From Operation annotation
    val serverAnnotations = collection.mutable.ArrayBuffer.empty[io.swagger.v3.oas.annotations.servers.Server]
    if (annotation != null) {
      serverAnnotations ++= annotation.servers
    }

    // From Server annotation
    val serverAnnotations2 = ReflectionUtils.getRepeatableAnnotations(method, classOf[io.swagger.v3.oas.annotations.servers.Server])
    if (serverAnnotations2 != null) {
      serverAnnotations ++= serverAnnotations2.asScala
    }

    if (serverAnnotations.nonEmpty) {
      val array = serverAnnotations.toArray[io.swagger.v3.oas.annotations.servers.Server]
      AnnotationsUtils.getServers(array)
        .toOption
        .foreach(list => list.asScala.foreach(operation.addServersItem))
    }

    // Tags
    if (annotation != null) {
      operation.setTags(annotation.tags.toList.asJava)
    }

    val tagAnnotations = ReflectionUtils.getRepeatableAnnotations(method, classOf[io.swagger.v3.oas.annotations.tags.Tag])
    if (tagAnnotations != null && !tagAnnotations.isEmpty) {
      val array = tagAnnotations.asScala.toArray[io.swagger.v3.oas.annotations.tags.Tag]
        AnnotationsUtils.getTags(array, false)
          .toOption
          .foreach(set => {
            val scalaSet = set.asScala
            tags ++= scalaSet
            scalaSet.foreach(tag => operation.addTagsItem(tag.getName))
          })
    }


    // Parameters
    if (annotation != null) {
      for (parameterAnnotation <- annotation.parameters) {
        parseParameter(parameterAnnotation).foreach(operation.addParametersItem)
      }
    }

    if (route != null) {
      getParameters(cls, method, route).foreach(operation.addParametersItem)
    }

    val parametersList = ReflectionUtils.getRepeatableAnnotations(method, classOf[io.swagger.v3.oas.annotations.Parameter])
    if (parametersList != null) {
      for(parameterAnnotation <- parametersList.asScala) {
        parseParameter(parameterAnnotation).foreach(operation.addParametersItem)
      }
    }

    // Request Body
    if (annotation != null) {
      OperationParser.getRequestBody(annotation.requestBody, null, null, components, null)
        .toOption
        .foreach(operation.setRequestBody)
    }

    val bodyAnnotation = ReflectionUtils.getAnnotation(method, classOf[io.swagger.v3.oas.annotations.parameters.RequestBody])
    if (bodyAnnotation != null) {
      OperationParser.getRequestBody(bodyAnnotation, null, null, components, null)
          .toOption
          .foreach(operation.setRequestBody)
    }

    // ExternalDocumentation
    if (annotation != null) {
      AnnotationsUtils.getExternalDocumentation(annotation.externalDocs).toOption.foreach(operation.setExternalDocs)
    }

    val docAnnotations = ReflectionUtils.getRepeatableAnnotations(method, classOf[io.swagger.v3.oas.annotations.ExternalDocumentation])
    if (docAnnotations != null){
      docAnnotations.asScala.foreach(annotation => {
        AnnotationsUtils.getExternalDocumentation(annotation)
          .toOption
          .foreach(operation.setExternalDocs)
      })
    }

    // Nothing from annotation
    if (operation.getExternalDocs == null) {
      classDocs.foreach(operation.setExternalDocs)
    }

    // Extensions
    if (annotation != null && annotation.extensions.nonEmpty) {
      AnnotationsUtils.getExtensions(annotation.extensions():_*).asScala.foreachEntry((k,v) => operation.addExtension(k, v))
    }

    if (responses.isEmpty) {
      val response = new ApiResponse()
      response.setDescription(SUCCESSFUL_OPERATION)
      responses.setDefault(response)
    }

    Some(operation)
  }

  private def parseOperation(annotation: io.swagger.v3.oas.annotations.Operation): Operation = {
    val operation = new Operation()
    val responses = new ApiResponses()
    operation.setResponses(responses)

    operation
      .operationId(annotation.operationId)
      .summary(annotation.summary)
      .description(annotation.description)
      .deprecated(annotation.deprecated)
      .tags(annotation.tags.toList.asJava)

    // Responses
    OperationParser.getApiResponses(annotation.responses, null, null, components, null)
      .toOption
      .foreach(responses.putAll)

    // Security
    SecurityParser.getSecurityRequirements(annotation.security)
      .toOption
      .foreach(list => list.asScala.foreach(operation.addSecurityItem))

    // Servers
    AnnotationsUtils.getServers(annotation.servers)
      .toOption
      .foreach(list => list.asScala.foreach(operation.addServersItem))

    // Parameters
    for (parameterAnnotation <- annotation.parameters) {
      parseParameter(parameterAnnotation).foreach(operation.addParametersItem)
    }

    // RequestBody
    OperationParser.getRequestBody(annotation.requestBody, null, null, components, null)
      .toOption
      .foreach(operation.setRequestBody)

    // External Documentation
    AnnotationsUtils.getExternalDocumentation(annotation.externalDocs).toOption.foreach(operation.setExternalDocs)

    // Extensions
    if (annotation.extensions.nonEmpty) {
      AnnotationsUtils.getExtensions(annotation.extensions():_*).asScala.foreachEntry((k,v) => operation.addExtension(k, v))
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

  private def parseCallback(annotation: io.swagger.v3.oas.annotations.callbacks.Callback): (String, Callback) = {
    val callbackObject = new Callback()
    if (isNotBlank(annotation.ref)) {
      callbackObject.set$ref(annotation.ref)
      return (annotation.name, callbackObject)
    }

    val pathItemObject = new PathItem()
    for(opAnnotation <- annotation.operation) {
      val operation = parseOperation(opAnnotation)
      val method = PathItem.HttpMethod.valueOf(opAnnotation.method.toUpperCase)
      pathItemObject.operation(method, operation)
    }
    callbackObject.addPathItem(annotation.callbackUrlExpression, pathItemObject)
    (annotation.name(), callbackObject)
  }

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
