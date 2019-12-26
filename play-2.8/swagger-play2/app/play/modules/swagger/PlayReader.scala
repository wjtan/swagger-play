package play.modules.swagger

//import io.swagger.v3.oas.annotations._
import io.swagger.v3.oas.annotations.{ Operation => ApiOperation }
import io.swagger.v3.oas.annotations.responses._
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.{ExternalDocumentation, _}
import io.swagger.v3.oas.models.info._
import io.swagger.v3.oas.models.tags.Tag
import io.swagger.v3.oas.models.media._
import io.swagger.v3.oas.models.parameters._
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.properties._
import io.swagger.v3.oas.models.security._
import io.swagger.v3.oas.models.servers._
import io.swagger.v3.oas.models.utils.PropertyModelConverter
import io.swagger.v3.core.util.BaseReaderUtils
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
import scala.util.control.Breaks._
import java.lang.annotation.Annotation
import java.lang.reflect.Method
import java.lang.reflect.Type
import java.util.regex.Pattern

import javax.inject.Inject


class PlayReader @Inject() (api: OpenAPI, routes: RouteWrapper, config: PlaySwaggerConfig) {
  private[this] val logger = Logger[PlayReader]

  private[this] val typeFactory = Json.mapper.getTypeFactory
  private[this] val modelConverters = ModelConverters.getInstance
  private[this] val propertyModelConverter = new PropertyModelConverter()
  
  val SUCCESSFUL_OPERATION = "successful operation"
  
  import StringUtils._
  
  def read(classes: Set[Class[_]]): OpenAPI = {
      // process SwaggerDefinitions first - so we get tags in desired order
      for (cls <- classes) {
          val swaggerDefinition = cls.getAnnotation(classOf[SwaggerDefinition])
          if (swaggerDefinition != null) {
              readSwaggerConfig(swaggerDefinition)
          }
      }
      
      for (cls <- classes) {
          read(cls)
      }
      swagger
  }
    
  def read(cls: Class[_]): OpenAPI = read(cls, readHidden = false)
  
  def read(cls: Class[_], readHidden: Boolean): OpenAPI = {
      val readable = cls.getAnnotation(classOf[Hidden])
      
      val tags = scala.collection.mutable.Map.empty[String, Tag]
      val securities = ListBuffer.empty[SecurityRequirement]
      var consumes = new Array[String](0)
      var produces = new Array[String](0)
      val globalSchemes = scala.collection.mutable.Set.empty[Schemes]

      //val readable = (api != null && readHidden) || (api != null && !api.hidden())
      val readable

      // TODO possibly allow parsing also without @Api annotation
      if (readable) {
          // the value will be used as a tag for 2.0 UNLESS a Tags annotation is present
          val tagStrings = extractTags(api)
          for (tagString <- tagStrings) {
              val tag = new Tag().name(tagString)
              tags.put(tagString, tag)
          }
          for (tagName <- tags.keys) {
              swagger.tag(tags(tagName))
          }

          if (!isEmpty(api.produces)) {
              produces = toArray(api.produces)
          }
          if (!isEmpty(api.consumes)) {
              consumes = toArray(api.consumes)
          }
          globalSchemes ++= parseSchemes(api.protocols)
          val authorizations = api.authorizations()

          for (auth <- authorizations) {
              if (!isEmpty(auth.value)) {
                  val scopes = auth.scopes
                  val addedScopes = scopes.toList.map(_.scope).filter(!isEmpty(_))
                  val security = new SecurityRequirement().requirement(auth.value, addedScopes.asJava)
                  
                  securities += security
              }
          }

          // parse the method
          val methods = cls.getMethods
          for (method <- methods) {
            breakable {
              if (ReflectionUtils.isOverriddenMethod(method, cls)) {
                  break
              }
              // complete name as stored in route
              val fullMethodName = getFullMethodName(cls, method)

              if (!routes.exists(fullMethodName)) {
                  break
              }
              val route = routes(fullMethodName)

              val operationPath = getPathFromRoute(route.path, config.basePath)

              if (operationPath != null) {
                  val apiOperation = ReflectionUtils.getAnnotation(method, classOf[Operation])

                  val httpMethod = extractOperationMethod(apiOperation, method, route)
                  val operation: Operation =
                    if (apiOperation != null || httpMethod != null) {
                      parseMethod(cls, method, route)
                    } else {
                      null
                    }

                  if (operation == null) {
                      break
                  }

                  if (apiOperation != null) {
                      for (scheme <- parseSchemes(apiOperation.protocols())) {
                          operation.scheme(scheme)
                      }
                  }

                  if (operation.getSchemes == null || operation.getSchemes.isEmpty) {
                      for (scheme <- globalSchemes) {
                          operation.scheme(scheme)
                      }
                  }
                  // can't continue without a valid http method
                  if (httpMethod != null) {
                      if (apiOperation != null) {
                          for (tag <- apiOperation.tags) {
                              if (!"".equals(tag)) {
                                  operation.tag(tag)
                                  swagger.tag(new Tag().name(tag))
                              }
                          }

                          operation.getVendorExtensions.putAll(BaseReaderUtils.parseExtensions(apiOperation.extensions()))
                      }
                      if (operation.getConsumes == null) {
                          for (mediaType <- consumes) {
                              operation.consumes(mediaType)
                          }
                      }
                      if (operation.getProduces == null) {
                          for (mediaType <- produces) {
                              operation.produces(mediaType)
                          }
                      }

                      if (operation.getTags == null) {
                          for (tagString <- tags.keys) {
                              operation.tag(tagString)
                          }
                      }
                      // Only add global @Api securities if operation doesn't already have more specific securities
                      if (operation.getSecurity == null) {
                          for (security <- securities) {
                              for(requirement <- security.getRequirements.asScala){
                                operation.addSecurity(requirement._1, requirement._2)
                              }
                          }
                      }
                      var path = swagger.getPath(operationPath)
                      if (path == null) {
                          path = new Path()
                          swagger.path(operationPath, path)
                      }
                      path.set(httpMethod, operation)
                      readImplicitParameters(method, operation, cls)
                  }
              }
            }
          }
      }

      swagger
  }

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

  private def readSwaggerConfig(config: OpenAPIDefinition): Unit = {
      //if (!isEmpty(config.basePath())) {
      //    swagger.setBasePath(config.basePath())
      //}

      if (!isEmpty(config.host())) {
          swagger.setHost(config.host())
      }

      readInfoConfig(config)

      for ( consume <- config.consumes()) {
          if (StringUtils.isNotEmpty(consume)) {
              swagger.addConsumes(consume)
          }
      }

      for ( produce <- config.produces()) {
          if (StringUtils.isNotEmpty(produce)) {
              swagger.addProduces(produce)
          }
      }

      if (!isEmpty(config.externalDocs.value())) {
          val externalDocs: ExternalDocs =
            if(swagger.getExternalDocs != null){
              swagger.getExternalDocs
            } else {
                val newExternalDocs = new ExternalDocs
                swagger.setExternalDocs(newExternalDocs)
                newExternalDocs
            }

          externalDocs.setDescription(config.externalDocs.value)

          if (!isEmpty(config.externalDocs.url)) {
              externalDocs.setUrl(config.externalDocs.url)
          }
      }

      for (tagConfig <- config.tags()) {
          if (!isEmpty(tagConfig.name())) {
              val tag = new Tag()
              tag.setName(tagConfig.name)
              tag.setDescription(tagConfig.description)

              if (!isEmpty(tagConfig.externalDocs.value)) {
                  tag.setExternalDocs(new ExternalDocs(tagConfig.externalDocs.value,
                                                       tagConfig.externalDocs.url))
              }

              tag.getVendorExtensions.putAll(BaseReaderUtils.parseExtensions(tagConfig.extensions))

              swagger.addTag(tag)
          }
      }

      for (scheme <- config.schemes()) {
          if (scheme != SwaggerDefinition.Scheme.DEFAULT) {
              swagger.addScheme(Scheme.forValue(scheme.name()))
          }
      }
  }

  private def readApiDefinition(annotation: io.swagger.v3.oas.annotations.OpenAPIDefinition): Unit = {
    api.setInfo(readInfo(annotation.info))
    api.setTags(annotation.tags.toList.map(readTag(_)).asJava)
    api.setExternalDocs(readExternalDocumentation(annotation.externalDocs))
    api.setServers(annotation.servers.toList.map(readServer(_)).asJava)
    api.setSecurity(annotation.security.toList.map(readSecurityRequirement(_)).asJava)
    api.setExtensions(readExtensions(annotation.extensions))
  }

  private def readInfo(annotation: io.swagger.v3.oas.annotations.info.Info): Info = {
    val obj = new Info()
    obj.setTitle(annotation.title)
    obj.setDescription(annotation.description)
    obj.setVersion(annotation.version)
    obj.setTermsOfService(annotation.termsOfService)
    obj.setContact(readContact(annotation.contact))
    obj.setLicense(readLicense(annotation.license))
    obj.setExtensions(readExtensions(annotation.extensions))
    obj
  }

  private def readContact(annotation: io.swagger.v3.oas.annotations.info.Contact): Contact = {
    val obj = new Contact()
    obj.setName(annotation.name)
    obj.setUrl(annotation.url)
    obj.setEmail(annotation.email)
    obj.setExtensions(readExtensions(annotation.extensions))
    obj
  }

  private def readLicense(annotation: io.swagger.v3.oas.annotations.info.License): License = {
    val obj = new License()
    obj.setName(annotation.name)
    obj.setUrl(annotation.url)
    obj.setExtensions(readExtensions(annotation.extensions))
    obj
  }

  private def readTag(annotation: io.swagger.v3.oas.annotations.tags.Tag): Tag = {
    val obj = new Tag()
    obj.setName(annotation.name)
    obj.setDescription(annotation.description)
    obj.setExternalDocs(readExternalDocumentation(annotation.externalDocs))
    obj.setExtensions(readExtensions(annotation.extensions))
    obj
  }

  private def readExternalDocumentation(annotation: io.swagger.v3.oas.annotations.ExternalDocumentation): ExternalDocumentation = {
    val obj = new ExternalDocumentation()
    obj.setUrl(annotation.url)
    obj.setDescription(annotation.description)
    obj.setExtensions(readExtensions(annotation.extensions))
    obj
  }

  private def readServer(annotation: io.swagger.v3.oas.annotations.servers.Server): Server = {
    val obj = new Server()
    obj.setUrl(annotation.url)
    obj.setDescription(annotation.description)
    obj.setVariables(readServerVariables(annotation.variables))
    obj.setExtensions(readExtensions(annotation.extensions))
    obj
  }

  private def readServerVariables(annotations: Array[io.swagger.v3.oas.annotations.servers.ServerVariable]): ServerVariables = {
    val obj = new ServerVariables()
    for(annotation <- annotations) {
      val obj2 = new ServerVariable()
      obj2.setEnum(java.util.Arrays.asList(annotation.allowableValues))
      obj2.setDescription(annotation.description)
      obj2.setDefault(annotation.defaultValue)
      obj2.extensions(readExtensions(annotation.extensions))
      obj.addServerVariable(annotation.name, obj2)
    }
    obj
  }

  private def readSecurityRequirement(annotation: io.swagger.v3.oas.annotations.security.SecurityRequirement): SecurityRequirement = {
    val obj = new SecurityRequirement()
    val list: java.util.List[String] = java.util.Arrays.asList(annotation.scopes)
    obj.addList(annotation.name, list)
    obj
  }

  private def readExtensions(annotations: Array[io.swagger.v3.oas.annotations.extensions.Extension]): java.util.Map[String, AnyRef] = {
    java.util.Collections.emptyMap[String, AnyRef]
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

  private def readImplicitParameters(method: Method, operation: Operation, cls: Class[_]): Unit = {
      val implicitParams = method.getAnnotation(classOf[ApiImplicitParams])
      if (implicitParams != null && implicitParams.value.length > 0) {
          for (param <- implicitParams.value()) {
              val p = readImplicitParam(param, cls)
              if (p != null) {
                  operation.addParameter(p)
              }
          }
      }
  }

  private def readImplicitParam(param: ApiImplicitParam, cls: Class[_]): io.swagger.models.parameters.Parameter = {
      val p: Parameter =
        if (param.paramType.equalsIgnoreCase("path")) {
            new PathParameter()
        } else if (param.paramType.equalsIgnoreCase("query")) {
            new QueryParameter()
        } else if (param.paramType.equalsIgnoreCase("form") || param.paramType.equalsIgnoreCase("formData")) {
            new FormParameter()
        } else if (param.paramType.equalsIgnoreCase("body")) {
            null
        } else if (param.paramType.equalsIgnoreCase("header")) {
            new HeaderParameter()
        } else {
            logger.warn("Unkown implicit parameter type: [" + param.paramType() + "]")
            return null
        }

      val t: Type =
      // Swagger ReflectionUtils can't handle file or array datatype
        if (!"".equalsIgnoreCase(param.dataType()) && !"file".equalsIgnoreCase(param.dataType()) && !"array".equalsIgnoreCase(param.dataType())) {
          typeFromString(param.dataType(), cls)
        } else {
          classOf[String]
        }

      val result = ParameterProcessor.applyAnnotations(swagger, p, t, java.util.Collections.singletonList(param))

      if (result.isInstanceOf[AbstractSerializableParameter[_]] && t != null) {
          val schema = createProperty(t)
          p.asInstanceOf[AbstractSerializableParameter[_]].setProperty(schema)
      }

      result

  }

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

  private def parseMethod(cls: Class[_], method: Method, route: Route): Operation = {
      val operation = new Operation()

      val apiOperation = ReflectionUtils.getAnnotation(method, classOf[ApiOperation])
      val responseAnnotation = ReflectionUtils.getAnnotation(method, classOf[ApiResponses])

      var operationId = method.getName
      operation.operationId(operationId)
      var responseContainer: String = null

      var responseType: Type = null
      //val defaultResponseHeaders = scala.collection.mutable.Map.empty[String, Property]

      if (apiOperation != null) {
          if (apiOperation.hidden()) {
              return null
          }
          if (!isEmpty(apiOperation.operationId)) {
              operationId = apiOperation.operationId
          }

          //defaultResponseHeaders ++= parseResponseHeaders(apiOperation.responseHeaders())

          operation.summary(apiOperation.summary)
                   .description(apiOperation.description)

          if (apiOperation.response() != null && !isVoid(apiOperation.response())) {
              responseType = apiOperation.response()
          }
          if (!isEmpty(apiOperation.responseContainer())) {
              responseContainer = apiOperation.responseContainer()
          }
          if (apiOperation.authorizations() != null) {
              for (auth <- apiOperation.authorizations()) {
                  if (!isEmpty(auth.value())) {
                      val scopes = auth.scopes()
                      val addedScopes = scopes.toList.map(_.scope).filter(!isEmpty(_))
                      operation.addSecurity(auth.value(), addedScopes.asJava)
                  }
              }
          }
          if (!isEmpty(apiOperation.consumes())) {
              operation.consumes(toArray(apiOperation.consumes).toList.asJava)
          }
          if (!isEmpty(apiOperation.produces())) {
              operation.produces(toArray(apiOperation.produces).toList.asJava)
          }
      }

      if (apiOperation != null && StringUtils.isNotEmpty(apiOperation.responseReference())) {
          val response = new Response().description(SUCCESSFUL_OPERATION)
          response.setResponseSchema(new RefModel(apiOperation.responseReference()))
          operation.addResponse(String.valueOf(apiOperation.code()), response)
      } else if (responseType == null) {
          // pick out response from method declaration
          val methodResponseType = method.getGenericReturnType

          if(!isResult(methodResponseType)){
            responseType = methodResponseType
          }
      }
      if (isValidResponse(responseType)) {
          val property = modelConverters.readAsProperty(responseType)
          if (property != null) {
              val responseProperty = toModel(wrapContainer(responseContainer, property))
              val responseCode: Int = Option(apiOperation).map(_.code()).getOrElse(200)
              val newResponse = new Response().description(SUCCESSFUL_OPERATION)
                                              .headers(defaultResponseHeaders.asJava)
              newResponse.setResponseSchema(responseProperty)
              operation.response(responseCode,newResponse)
              appendModels(responseType)
          }
      }


      operation.operationId(operationId)

      if (responseAnnotation != null) {
          for (apiResponse <- responseAnnotation.value()) {
              val responseHeaders = parseResponseHeaders(apiResponse.responseHeaders())

              val response = new Response().description(apiResponse.message())
                                           .headers(responseHeaders.asJava)

              if (apiResponse.code() == 0) {
                  operation.defaultResponse(response)
              } else {
                  operation.response(apiResponse.code(), response)
              }

              if (!isEmpty(apiResponse.reference())) {
                  response.setResponseSchema(new RefModel(apiResponse.reference()))
              } else if (!isVoid(apiResponse.response())) {
                  val responseType2 = apiResponse.response()
                  val property = modelConverters.readAsProperty(responseType2)
                  if (property != null) {
                    val container = toModel(wrapContainer(apiResponse.responseContainer(), property))
                    response.setResponseSchema(container)
                    appendModels(responseType2)
                  }
              }
          }
      }
      if (ReflectionUtils.getAnnotation(method, classOf[Deprecated]) != null) {
          operation.setDeprecated(true)
      }

      val parameters = getParameters(cls, method, route)

      for(parameter <- parameters){
        operation.parameter(parameter)
      }

      if (operation.getResponses == null) {
          val response = new Response().description(SUCCESSFUL_OPERATION)
          operation.defaultResponse(response)
      }
      operation
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
          val parameterType = primitiveTypes(enhancedType)
          typeFactory.constructType(parameterType)
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

  private def getParameters(cls: Class[_], method: Method, route: Route): List[Parameter] = {
      // TODO now consider only parameters defined in route, excluding body parameters
      // understand how to possibly infer body/form params e.g. from @BodyParser or other annotation

      if (route.call.parameters.isEmpty) {
          return List.empty
      }

      val parameters = ListBuffer.empty[Parameter]

      for((p, fieldPosition) <- route.call.parameters.get.zipWithIndex){
          if (p.fixed.isEmpty) {
            var defaultField = CrossUtil.getParameterDefaultField(p)
            if (defaultField.startsWith("\"") && defaultField.endsWith("\"")) {
                defaultField = defaultField.substring(1, defaultField.length() - 1)
            }
            val t = getParamType(cls, method, p.typeName, fieldPosition)

            // Ignore play.mvc.Http.Request
            if(!t.getTypeName.equals("[simple type, class play.mvc.Http$Request]")){
              val schema = createProperty(t)

              val parameter: Parameter =
                if (route.path.has(p.name)) {
                    // it's a path param
                    val pathParameter = new PathParameter()
                    pathParameter.setDefaultValue(defaultField)
                    if (schema != null) {
                        pathParameter.setProperty(schema)
                    }
                    pathParameter
                } else {
                    // it's a query string param
                    val queryParameter = new QueryParameter()
                    queryParameter.setDefaultValue(defaultField)
                    if (schema != null) {
                        queryParameter.setProperty(schema)
                    }
                    queryParameter
                }
              parameter.setName(p.name)
              val annotations = getParamAnnotations(cls, method, p.typeName, fieldPosition)
              ParameterProcessor.applyAnnotations(swagger, parameter, t, annotations.asJava)
              parameters += parameter
            }
          }
      }
      parameters.toList
  }

  private def parseSchemes(schemes: String): Set[Scheme] = {
      val result = scala.collection.mutable.Set.empty[Scheme]
      for (item <- trimToEmpty(schemes).split(",")) {
          val scheme = Scheme.forValue(trimToNull(item))
          if (scheme != null) {
              result += scheme
          }
      }
      result.toSet
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

  private def isValidResponse(t: Type): Boolean =  {
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

  private def isResourceClass(cls: Class[_]) = cls.getAnnotation(classOf[Api]) != null

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
  }

  private def createProperty(t: Type): Property = enforcePrimitive(modelConverters.readAsProperty(t), 0)

  private def enforcePrimitive(in: Schema[_], level: Int): Schema[_] = {
      if (in.isInstanceOf[RefProperty]) {
          return new StringSchema()
      }
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

  private def appendModels(t: Type): Unit = {
      val models = modelConverters.readAll(t)
      for ((modelName, model) <- models.asScala) {
        api.schema(modelName, model)
      }
  }

  private def parseResponseHeaders(headers: Array[io.swagger.v3.oas.annotations.headers.Header]): Map[String, Schema[_]] = {
      val responseHeaders = scala.collection.mutable.Map.empty[String, Schema[_]]
      if (headers != null && headers.length > 0) {
          for (header <- headers) {
              val name = header.name()
              if (!isEmpty(name)) {
                  val description = header.description()
                  val cls = header.response()

                  if (!isVoid(cls)) {
                      val property = modelConverters.readAsProperty(cls)
                      if (property != null) {
                          val responseProperty = wrapContainer(header.responseContainer(), property,
                                                                                     ContainerWrapper.ARRAY, ContainerWrapper.LIST,
                                                                                     ContainerWrapper.SET)
                          responseProperty.setDescription(description)
                          responseHeaders.put(name, responseProperty)
                          appendModels(cls)
                      }
                  }
              }
          }
      }
      responseHeaders.toMap
  }

  private def getFullMethodName(clazz: Class[_], method: Method): String = {
      if (!clazz.getCanonicalName.contains("$")) {
          clazz.getCanonicalName + "$." + method.getName
      } else {
          clazz.getCanonicalName + "." + method.getName
      }
  }

  private def extractOperationMethod(apiOperation: ApiOperation, method: Method, route: Route): String =  {
    if (route != null) {
        try {
            return route.verb.toString.toLowerCase()
        } catch {
          case e: Exception => logger.error(s"http method not found for method: ${method.getName}", e)
        }
    }
    if (!isEmpty(apiOperation.method())) {
        return apiOperation.method()
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
    type Wrapper = (String, (Schema[_]) => Schema[_])
    
    val LIST: Wrapper = ("list", wrapList)
    val ARRAY: Wrapper = ("array", wrapList)
    val MAP: Wrapper = ("map", wrapMap)
    val SET: Wrapper = ("set", wrapSet)
    
    val ALL = List(LIST, ARRAY, MAP, SET)
    
    def wrapList(schema: Schema[_]): Schema[_] = new ArraySchema(schema)
    def wrapMap(schema: Schema[_]): Schema[_] = new MapSchema(schema)
    def wrapSet(schema: Schema[_]): Schema[_] = {
      val arrayProperty = new ArraySchema(schema)
      arrayProperty.setUniqueItems(true)
      arrayProperty
    }
  }
  
  private def wrapContainer(container: String, property: Schema[_], allowed: ContainerWrapper.Wrapper*): Schema[_] = {
    val wrappers =
      if(allowed.isEmpty){
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
