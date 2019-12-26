package play.modules.swagger

import com.fasterxml.jackson.databind.JavaType
import io.swagger.annotations._
import io.swagger.annotations.Info
import io.swagger.converter.ModelConverters
import io.swagger.models._
import io.swagger.models.Contact
import io.swagger.models.ExternalDocs
import io.swagger.models.Tag
import io.swagger.models.parameters._
import io.swagger.models.parameters.Parameter
import io.swagger.models.properties._
import io.swagger.util.BaseReaderUtils
import io.swagger.util.Json
import io.swagger.util.ParameterProcessor
import io.swagger.util.PrimitiveType
import io.swagger.util.ReflectionUtils
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
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.inject.Inject


class PlayReader @Inject() (swagger: Swagger, routes: RouteWrapper, config: PlaySwaggerConfig) {
  private[this] val logger = Logger[PlayReader]

  private[this] val typeFactory = Json.mapper().getTypeFactory()
  private[this] val modelConverters = ModelConverters.getInstance()
  
  val SUCCESSFUL_OPERATION = "successful operation"
  
  import StringUtils._
  
  def read(classes: Set[Class[_]]): Swagger = {
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
      return swagger
  }
    
  def read(cls: Class[_]): Swagger = read(cls, false)
  
  def read(cls: Class[_], readHidden: Boolean): Swagger = {
      val api = cls.getAnnotation(classOf[Api])
      
      val tags = scala.collection.mutable.Map.empty[String, Tag]
      val securities = ListBuffer.empty[SecurityRequirement]
      var consumes = new Array[String](0)
      var produces = new Array[String](0)
      val globalSchemes = scala.collection.mutable.Set.empty[Scheme]

      val readable = (api != null && readHidden) || (api != null && !api.hidden())

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

          if (!isEmpty(api.produces())) {
              produces = toArray(api.produces())
          }
          if (!isEmpty(api.consumes())) {
              consumes = toArray(api.consumes())
          }
          globalSchemes ++= parseSchemes(api.protocols())
          val authorizations = api.authorizations()

          for (auth <- authorizations) {
              if (!isEmpty(auth.value())) {
                  val scopes = auth.scopes()
                  val addedScopes = scopes.toList.map(_.scope).filter(!isEmpty(_))
                  val security = new SecurityRequirement().requirement(auth.value(), addedScopes.asJava)
                  
                  securities += security
              }
          }

          // parse the method
          val methods = cls.getMethods()
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
                  val apiOperation = ReflectionUtils.getAnnotation(method, classOf[ApiOperation])

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

                  if (operation.getSchemes() == null || operation.getSchemes().isEmpty()) {
                      for (scheme <- globalSchemes) {
                          operation.scheme(scheme)
                      }
                  }
                  // can't continue without a valid http method
                  if (httpMethod != null) {
                      if (apiOperation != null) {
                          for (tag <- apiOperation.tags()) {
                              if (!"".equals(tag)) {
                                  operation.tag(tag)
                                  swagger.tag(new Tag().name(tag))
                              }
                          }

                          operation.getVendorExtensions().putAll(BaseReaderUtils.parseExtensions(apiOperation.extensions()))
                      }
                      if (operation.getConsumes() == null) {
                          for (mediaType <- consumes) {
                              operation.consumes(mediaType)
                          }
                      }
                      if (operation.getProduces() == null) {
                          for (mediaType <- produces) {
                              operation.produces(mediaType)
                          }
                      }

                      if (operation.getTags() == null) {
                          for (tagString <- tags.keys) {
                              operation.tag(tagString)
                          }
                      }
                      // Only add global @Api securities if operation doesn't already have more specific securities
                      if (operation.getSecurity() == null) {
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

      return swagger
  }

  private def getPathFromRoute(pathPattern: PathPattern, basePath: String): String = {
      val sb = new StringBuilder()
      val iter = pathPattern.parts.iterator
      while (iter.hasNext) {
          val part = iter.next.asInstanceOf[PathPart]
          if (part.isInstanceOf[StaticPart]) {
              sb.append(part.asInstanceOf[StaticPart].value)
          } else if (part.isInstanceOf[DynamicPart]) {
              sb.append("{")
              sb.append(part.asInstanceOf[DynamicPart].name)
              sb.append("}")
          } else {
            logger.warn("ClassCastException parsing path from route {}", part.getClass.getSimpleName)
          }
      }
      val operationPath = new StringBuilder()
      val newBasePath =
        if (basePath.startsWith("/")) {
          basePath.substring(1)
        } else {
          basePath
        }
      operationPath.append(sb.toString().replaceFirst(newBasePath, ""))
      if (!operationPath.toString().startsWith("/")) {
          operationPath.insert(0, "/")
      }
      return operationPath.toString()
  }

  private def readSwaggerConfig(config: SwaggerDefinition): Unit = {
      if (!isEmpty(config.basePath())) {
          swagger.setBasePath(config.basePath())
      }

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

      if (!isEmpty(config.externalDocs().value())) {
          val externalDocs: ExternalDocs = 
            if(swagger.getExternalDocs() != null){
              swagger.getExternalDocs()
            } else {
                val newExternalDocs = new ExternalDocs()
                swagger.setExternalDocs(newExternalDocs)
                newExternalDocs
            }

          externalDocs.setDescription(config.externalDocs().value())

          if (!isEmpty(config.externalDocs().url())) {
              externalDocs.setUrl(config.externalDocs().url())
          }
      }

      for (tagConfig <- config.tags()) {
          if (!isEmpty(tagConfig.name())) {
              val tag = new Tag()
              tag.setName(tagConfig.name())
              tag.setDescription(tagConfig.description())

              if (!isEmpty(tagConfig.externalDocs().value())) {
                  tag.setExternalDocs(new ExternalDocs(tagConfig.externalDocs().value(),
                                                       tagConfig.externalDocs().url()))
              }

              tag.getVendorExtensions().putAll(BaseReaderUtils.parseExtensions(tagConfig.extensions()))

              swagger.addTag(tag)
          }
      }

      for (scheme <- config.schemes()) {
          if (scheme != SwaggerDefinition.Scheme.DEFAULT) {
              swagger.addScheme(Scheme.forValue(scheme.name()))
          }
      }
  }

  private def readInfoConfig(config: SwaggerDefinition): Unit = {
      val infoConfig = config.info()
      val info: io.swagger.models.Info = 
        if (swagger.getInfo() != null) {
          swagger.getInfo()
        } else {
            val newInfo = new io.swagger.models.Info()
            swagger.setInfo(newInfo)
            newInfo
        }

      if (!isEmpty(infoConfig.description())) {
          info.setDescription(infoConfig.description())
      }

      if (!isEmpty(infoConfig.termsOfService())) {
          info.setTermsOfService(infoConfig.termsOfService())
      }

      if (!isEmpty(infoConfig.title())) {
          info.setTitle(infoConfig.title())
      }

      if (!isEmpty(infoConfig.version())) {
          info.setVersion(infoConfig.version())
      }

      if (!isEmpty(infoConfig.contact().name())) {
          val contact: Contact = 
            if(info.getContact() != null){
              info.getContact()
            } else {
              val newContact = new Contact()
              info.setContact(newContact)
              newContact
            }

          contact.setName(infoConfig.contact().name())
          if (!isEmpty(infoConfig.contact().email())) {
              contact.setEmail(infoConfig.contact().email())
          }

          if (!isEmpty(infoConfig.contact().url())) {
              contact.setUrl(infoConfig.contact().url())
          }
      }

      if (!isEmpty(infoConfig.license().name())) {
          val license: io.swagger.models.License = 
            if(info.getLicense() != null){
              info.getLicense()
            } else {
              val newLicense = new io.swagger.models.License()
              info.setLicense(newLicense)
              newLicense
            }

          license.setName(infoConfig.license().name())
          if (!isEmpty(infoConfig.license().url())) {
              license.setUrl(infoConfig.license().url())
          }
      }

      info.getVendorExtensions().putAll(BaseReaderUtils.parseExtensions(infoConfig.extensions()))
  }

  private def readImplicitParameters(method: Method, operation: Operation, cls: Class[_]): Unit = {
      val implicitParams = method.getAnnotation(classOf[ApiImplicitParams])
      if (implicitParams != null && implicitParams.value().length > 0) {
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
        if (param.paramType().equalsIgnoreCase("path")) {
            new PathParameter()
        } else if (param.paramType().equalsIgnoreCase("query")) {
            new QueryParameter()
        } else if (param.paramType().equalsIgnoreCase("form") || param.paramType().equalsIgnoreCase("formData")) {
            new FormParameter()
        } else if (param.paramType().equalsIgnoreCase("body")) {
            null
        } else if (param.paramType().equalsIgnoreCase("header")) {
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

      return result

  }

  private def typeFromString(t: String, cls: Class[_]): Type = {
      val primitive = PrimitiveType.fromName(t)
      if (primitive != null) {
          return primitive.getKeyClass()
      }
      try {
          val routeType = getOptionTypeFromString(t, cls)

          if (routeType != null) {
              return routeType
          }
          
          return Thread.currentThread().getContextClassLoader().loadClass(t)
      } catch {
        case e: Exception => logger.error(s"Failed to resolve '$t' into class", e)
      }
      return null
  }
  
  private def parseMethod(cls: Class[_], method: Method, route: Route): Operation = {
      val operation = new Operation()
      
      val apiOperation = ReflectionUtils.getAnnotation(method, classOf[ApiOperation])
      val responseAnnotation = ReflectionUtils.getAnnotation(method, classOf[ApiResponses])
      
      var operationId = method.getName()
      operation.operationId(operationId)
      var responseContainer: String = null
      
      var responseType: Type = null
      val defaultResponseHeaders = scala.collection.mutable.Map.empty[String, Property]
      
      if (apiOperation != null) {
          if (apiOperation.hidden()) {
              return null
          }
          if (!isEmpty(apiOperation.nickname())) {
              operationId = apiOperation.nickname()
          }
          
          defaultResponseHeaders ++= parseResponseHeaders(apiOperation.responseHeaders())
          
          operation.summary(apiOperation.value())
                   .description(apiOperation.notes())
                   
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
              operation.consumes(toArray(apiOperation.consumes()).toList.asJava)
          }
          if (!isEmpty(apiOperation.produces())) {
              operation.produces(toArray(apiOperation.produces()).toList.asJava)
          }
      }
      
      if (apiOperation != null && StringUtils.isNotEmpty(apiOperation.responseReference())) {
          val response = new Response().description(SUCCESSFUL_OPERATION)
          response.schema(new RefProperty(apiOperation.responseReference()))
          operation.addResponse(String.valueOf(apiOperation.code()), response)
      } else if (responseType == null) {
          // pick out response from method declaration
          val methodResponseType = method.getGenericReturnType()
          
          if(!isResult(methodResponseType)){
            responseType = methodResponseType
          }
      }
      if (isValidResponse(responseType)) {
          val property = modelConverters.readAsProperty(responseType)
          if (property != null) {
              val responseProperty = wrapContainer(responseContainer, property)
              val responseCode: Int = Option(apiOperation).map(_.code()).getOrElse(200)
              operation.response(responseCode, new Response().description(SUCCESSFUL_OPERATION).schema(responseProperty)
                                                             .headers(defaultResponseHeaders.asJava))
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
                  response.schema(new RefProperty(apiResponse.reference()))
              } else if (!isVoid(apiResponse.response())) {
                  val responseType2 = apiResponse.response()
                  val property = modelConverters.readAsProperty(responseType2)
                  if (property != null) {
                    response.schema(wrapContainer(apiResponse.responseContainer(), property))
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
      
      if (operation.getResponses() == null) {
          val response = new Response().description(SUCCESSFUL_OPERATION)
          operation.defaultResponse(response)
      }
      return operation
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
          return typeFactory.constructType(parameterType)
      } else {
          return null
      }
  }
  
  private def getParamType(cls: Class[_], method: Method, simpleTypeName: String, position: Int): Type = {
      try {
          val t = getOptionTypeFromString(simpleTypeName, cls)
          if (t != null) {
              return t
          }
          
          val genericParameterTypes = method.getGenericParameterTypes()
          val parameterType = genericParameterTypes(position)
          return typeFactory.constructType(parameterType)
      } catch {
        case e: Exception => {
          logger.error(s"Exception getting parameter type for method $method, param $simpleTypeName at position $position", e)
          return null
        }
      }
  }
  
  private def getParamAnnotations(cls: Class[_], genericParameterTypes: Array[Type], paramAnnotations: Array[Array[Annotation]], simpleTypeName: String, fieldPosition: Int): List[Annotation] = {
      try {
        return paramAnnotations(fieldPosition).toList
      } catch {
        case e: Exception => {
          logger.error(s"Exception getting parameter type for $simpleTypeName at position $fieldPosition", e)
          return List.empty
        }
      }
  }
  
  private def getParamAnnotations(cls: Class[_], method: Method, simpleTypeName: String, fieldPosition: Int): List[Annotation] = {
      val genericParameterTypes = method.getGenericParameterTypes()
      val paramAnnotations = method.getParameterAnnotations()
      val annotations = getParamAnnotations(cls, genericParameterTypes, paramAnnotations, simpleTypeName, fieldPosition)
      if (!annotations.isEmpty) {
          return annotations
      }
      
      // Fallback to type
      for (i <- 0 until genericParameterTypes.length) {
          val annotations2 = getParamAnnotations(cls, genericParameterTypes, paramAnnotations, simpleTypeName, i)
          if (!annotations2.isEmpty) {
              return annotations2
          }
      }
      
      return List.empty
  }
  
  private def getParameters(cls: Class[_], method: Method, route: Route): List[Parameter] = {
      // TODO now consider only parameters defined in route, excluding body parameters
      // understand how to possibly infer body/form params e.g. from @BodyParser or other annotation
      
      if (!route.call.parameters.isDefined) {
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
      return parameters.toList
  }
  
  private def parseSchemes(schemes: String): Set[Scheme] = {
      val result = scala.collection.mutable.Set.empty[Scheme]
      for (item <- trimToEmpty(schemes).split(",")) {
          val scheme = Scheme.forValue(trimToNull(item))
          if (scheme != null) {
              result += scheme
          }
      }
      return result.toSet
  }
  
  private def isVoid(t: Type): Boolean = {
    val cls = typeFactory.constructType(t).getRawClass()
    return classOf[Void].isAssignableFrom(cls) || Void.TYPE.isAssignableFrom(cls)
  }
  
  // Play Result
  private def isResult(t: Type): Boolean = {
    val cls = typeFactory.constructType(t).getRawClass()
    return classOf[play.api.mvc.Result].isAssignableFrom(cls) || classOf[play.mvc.Result].isAssignableFrom(cls) 
  }
  
  private def isValidResponse(t: Type): Boolean =  {
      if (t == null) {
          return false
      }
      val javaType = typeFactory.constructType(t)
      if (isVoid(javaType)) {
          return false
      }
      val cls = javaType.getRawClass()
      return !isResourceClass(cls)
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
          val tagString = api.value().replace("/", "")
          if (!tagString.isEmpty) {
              output.add(tagString)
          }
      }
      return output.toSet
  }
  
  private def createProperty(t: Type): Property = enforcePrimitive(modelConverters.readAsProperty(t), 0)
  
  private def enforcePrimitive(in: Property, level: Int): Property = {
      if (in.isInstanceOf[RefProperty]) {
          return new StringProperty()
      }
      if (in.isInstanceOf[ArrayProperty]) {
          if (level == 0) {
              val array = in.asInstanceOf[ArrayProperty]
              array.setItems(enforcePrimitive(array.getItems(), level + 1))
          } else {
              return new StringProperty()
          }
      }
      return in
  }
  
  private def appendModels(t: Type): Unit = {
      val models = modelConverters.readAll(t)
      for ((modelName, model) <- models.asScala) {
        swagger.model(modelName, model)
      }
  }
  
  private def parseResponseHeaders(headers: Array[ResponseHeader]): Map[String, Property] = {
      val responseHeaders = scala.collection.mutable.Map.empty[String, Property]
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
      return responseHeaders.toMap
  }
  
  private def getFullMethodName(clazz: Class[_], method: Method): String = {
      if (!clazz.getCanonicalName().contains("$")) {
          return clazz.getCanonicalName() + "$." + method.getName()
      } else {
          return clazz.getCanonicalName() + "." + method.getName()
      }
  }
  
  private def extractOperationMethod(apiOperation: ApiOperation, method: Method, route: Route): String =  {
    if (route != null) {
        try {
            return route.verb.toString().toLowerCase()
        } catch {
          case e: Exception => logger.error(s"http method not found for method: ${method.getName()}", e)
        }
    }
    if (!isEmpty(apiOperation.httpMethod())) {
        return apiOperation.httpMethod()
    }
    return null
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
      return result
  }
  
  private object ContainerWrapper {
    type Wrapper = (String, (Property) => Property)
    
    val LIST: Wrapper = ("list", wrapList)
    val ARRAY: Wrapper = ("array", wrapList)
    val MAP: Wrapper = ("map", wrapMap)
    val SET: Wrapper = ("set", wrapSet)
    
    val ALL = List(LIST, ARRAY, MAP, SET)
    
    def wrapList(property: Property): Property = new ArrayProperty(property)
    def wrapMap(property: Property): Property = new MapProperty(property)
    def wrapSet(property: Property): Property = {
      val arrayProperty = new ArrayProperty(property)
      arrayProperty.setUniqueItems(true)
      arrayProperty
    }
  }
  
  private def wrapContainer(container: String, property: Property, allowed: ContainerWrapper.Wrapper*): Property = {
    val wrappers =
      if(allowed.size == 0){
        ContainerWrapper.ALL
      } else {
        allowed
      }
      
    for (wrapper <- wrappers) {
        if (wrapper._1.equalsIgnoreCase(container)) {
          return wrapper._2.apply(property)
        }
    }
    return property
  }
}
