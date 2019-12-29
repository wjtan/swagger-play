import java.io.File

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.{OpenAPI, PathItem}
import io.swagger.v3.oas.models.PathItem.HttpMethod
import io.swagger.v3.oas.models.media.{ArraySchema, Schema}
import io.swagger.v3.oas.models.parameters._
import play.modules.swagger._
import play.routes.compiler.{Route => PlayRoute}
import org.junit.runner.RunWith
import org.specs2.mutable._
import org.specs2.mock.Mockito
import org.specs2.runner.JUnitRunner

import scala.jdk.CollectionConverters._
import org.slf4j.LoggerFactory
import play.modules.swagger.util.SwaggerScalaModelConverter

@RunWith(classOf[JUnitRunner])
class PlayApiListingCacheSpec extends Specification with Mockito {
  val logger = LoggerFactory.getLogger("play.modules.swagger")

  //GET /api/search2 testdata.SettlementsSearcherController.searchToo(req:Request,propertyId:String)

  // set up mock for Play Router
  val routesList = {
    play.routes.compiler.RoutesFileParser.parseContent("""
POST /api/document/:settlementId/files/:fileId/accept testdata.DocumentController.accept(settlementId:String,fileId:String)
GET /api/search testdata.SettlementsSearcherController.search(req: Request, personalNumber:String, propertyId:String)
GET /api/pointsofinterest testdata.PointOfInterestController.list(eastingMin:Double,northingMin:Double,eastingMax:Double,northingMax:Double)
GET /api/dog testdata.DogController.list
PUT /api/dog testdata.DogController.add1
GET /api/cat @testdata.CatController.list
GET /api/cat43 @testdata.CatController.testIssue43(test_issue_43_param: Option[Int])
PUT /api/cat @testdata.CatController.add1
GET /api/fly testdata.FlyController.list
PUT /api/dog testdata.DogController.add1
PUT /api/dog/:id testdata.DogController.add0(id:String)
    """, new File("")).getOrElse(List.empty).collect {
      case (route: PlayRoute) =>
        route
    }
  }

  val routesRules = RouteProvider.buildRouteRules(routesList) 

  val apiVersion = "test1"
  val basePath = "/api"

  val swaggerConfig = PlaySwaggerConfig(
    description = "description",
    basePath = basePath,
    contact = "contact",
    host = "127.0.0.1",
    version = "beta",
    title = "title",
    termsOfServiceUrl = "http://termsOfServiceUrl",
    license = "license",
    licenseUrl = "http://licenseUrl")

  val route = new RouteWrapper(routesRules)
  val scanner = new PlayApiScanner(swaggerConfig, route)

  ModelConverters.getInstance().addConverter(new SwaggerScalaModelConverter())
  val readerProvider = new PlayReaderProvider(route, swaggerConfig)
  // val reader = new PlayReader(swagger, route, swaggerConfig)

  "ApiListingCache" should {

    "load all API specs" in {

      val docRoot = ""
      val api = new ApiListingCache(readerProvider, scanner).listing(docRoot, "127.0.0.1")

      logger.debug("swagger: " + toJsonString(api))
      api must beSome

      api.get.getOpenapi must beEqualTo("3.0.1")
      //api.get.getBasePath must beEqualTo(basePath)
      api.get.getPaths.size must beEqualTo(7)
      api.get.getComponents.getSchemas.size must beEqualTo(3)
      //api.get.getHost must beEqualTo(swaggerConfig.host)
      api.get.getInfo.getContact.getName must beEqualTo(swaggerConfig.contact)
      api.get.getInfo.getVersion must beEqualTo(swaggerConfig.version)
      api.get.getInfo.getTitle must beEqualTo(swaggerConfig.title)
      api.get.getInfo.getTermsOfService must beEqualTo(swaggerConfig.termsOfServiceUrl)
      api.get.getInfo.getLicense.getName must beEqualTo(swaggerConfig.license)

      val pathDoc = api.get.getPaths.get("/document/{settlementId}/files/{fileId}/accept")
      pathDoc.readOperationsMap.size must beEqualTo(1)

      val opDocPost = pathDoc.readOperationsMap.get(HttpMethod.POST)
      opDocPost.getOperationId must beEqualTo("acceptsettlementfile")
      opDocPost.getParameters.size() must beEqualTo(3)
      opDocPost.getParameters.get(0).getDescription must beEqualTo("Id of the settlement to accept a file on.")
      opDocPost.getParameters.get(1).getDescription must beEqualTo("File id of the file to accept.")

      val pathSearch = api.get.getPaths.get("/search")
      pathSearch.readOperationsMap.size must beEqualTo(1)

      val opSearchGet = pathSearch.readOperationsMap.get(HttpMethod.GET)
      opSearchGet.getParameters.size() must beEqualTo(3)
      opSearchGet.getDescription must beEqualTo("Search for a settlement with personal number and property id.")
      opSearchGet.getParameters.get(0).getDescription must beEqualTo("A personal number of one of the sellers.")
      opSearchGet.getParameters.get(1).getDescription must beEqualTo("The cadastre or share id.")

      //val pathSearch2 = swagger.get.getPaths.get("/search2")
      //pathSearch2.readOperationsMap.size must beEqualTo(1)

      //val opSearchGet2 = pathSearch2.readOperationsMap.get(HttpMethod.GET)
      //opSearchGet2.getParameters.size() must beEqualTo(1)
      //opSearchGet2.getParameters.get(0).getDescription must beEqualTo("The cadastre or share id.")

      val pathPOI = api.get.getPaths.get("/pointsofinterest")
      pathPOI.readOperationsMap.size must beEqualTo(1)

      val opPOIGet = pathPOI.readOperationsMap.get(HttpMethod.GET)
      opPOIGet.getParameters.size() must beEqualTo(5)
      opPOIGet.getDescription must beEqualTo("Returns points of interest")
      opPOIGet.getParameters.get(0).getDescription must beEqualTo("Minimum easting for provided extent")
      opPOIGet.getParameters.get(1).getDescription must beEqualTo("Minimum northing for provided extent")
      opPOIGet.getParameters.get(2).getDescription must beEqualTo("Maximum easting for provided extent")
      opPOIGet.getParameters.get(3).getDescription must beEqualTo("Maximum northing for provided extent")

      val pathCat = api.get.getPaths.get("/cat")
      pathCat.readOperationsMap.size must beEqualTo(2)

      val opCatGet = pathCat.readOperationsMap.get(HttpMethod.GET)
      opCatGet.getOperationId must beEqualTo("listCats")
      opCatGet.getParameters.asScala must beNull
      opCatGet.getResponses.get("default").getContent.asScala.toList.head._2.getSchema.asInstanceOf[ArraySchema].getItems.get$ref must beEqualTo("#/components/schemas/Cat")

      val opCatPut = pathCat.readOperationsMap.get(HttpMethod.PUT)
      opCatPut.getOperationId must beEqualTo("add1")
      opCatPut.getRequestBody must not(beNull)
      opCatPut.getRequestBody.getContent.asScala.toList.head._2.getSchema.get$ref must beEqualTo("#/components/schemas/Cat")
      opCatPut.getResponses.get("default").getContent.asScala.toList.head._2.getSchema.getType must beEqualTo("string")

      val pathCat43 = api.get.getPaths.get("/cat43")
      pathCat43.readOperationsMap.size must beEqualTo(1)

      val opCatGet43 = pathCat43.readOperationsMap.get(HttpMethod.GET)
      opCatGet43.getOperationId must beEqualTo("test issue #43_nick")
      opCatGet43.getResponses.get("default").getContent.asScala.toList.head._2.getSchema.asInstanceOf[ArraySchema].getItems.get$ref must beEqualTo("#/components/schemas/Cat")

      opCatGet43.getParameters.get(0).getName must beEqualTo("test_issue_43_param")
      opCatGet43.getParameters.get(0).getIn must beEqualTo("query")
      opCatGet43.getParameters.get(0).getSchema.getType must beEqualTo("integer")

      opCatGet43.getParameters.get(1).getName must beEqualTo("test_issue_43_implicit_param")
      opCatGet43.getParameters.get(1).getIn must beEqualTo("query")
      opCatGet43.getParameters.get(1).getSchema.getType must beEqualTo("integer")

      val pathDog = api.get.getPaths.get("/dog")
      pathDog.readOperations.size must beEqualTo(2)

      val opDogGet = pathDog.readOperationsMap.get(HttpMethod.GET)
      opDogGet.getOperationId must beEqualTo("listDogs")
      opDogGet.getParameters must beNull
      //opDogGet.getConsumes.asScala.toList must beEqualTo(List("application/json", "application/xml"))
      opDogGet.getResponses.get("default").getContent.asScala.toList.head._2.getSchema.asInstanceOf[ArraySchema].getItems.get$ref must beEqualTo("#/components/schemas/Dog")
      //opDogGet.getProduces.asScala.toList must beEqualTo(List("application/json", "application/xml"))

      val opDogPut = pathDog.readOperationsMap.get(HttpMethod.PUT)
      opDogPut.getOperationId must beEqualTo("addDog1")
      opCatPut.getRequestBody must not(beNull)
      opDogPut.getRequestBody.getContent.asScala.toList.head._2.getSchema.get$ref must beEqualTo("#/components/schemas/Dog")
      //opDogPut.getConsumes.asScala.toList must beEqualTo(List("application/json", "application/xml"))
      opDogPut.getResponses.get("default").getContent.asScala.toList.head._2.getSchema.getType must beEqualTo("string")
      //opDogPut.getProduces.asScala.toList must beEqualTo(List("application/json", "application/xml"))

      val pathDogParam = api.get.getPaths.get("/dog/{id}")
      pathDogParam.readOperationsMap.size must beEqualTo(1)

      val opDogParamPut = pathDogParam.readOperationsMap.get(HttpMethod.PUT)
      opDogParamPut.getOperationId must beEqualTo("addDog0")
      opDogParamPut.getParameters.asScala.head.getName must beEqualTo("id")
      opDogParamPut.getParameters.asScala.head.getIn must beEqualTo("path")
      opDogParamPut.getParameters.asScala.head.asInstanceOf[PathParameter].getSchema.getType must beEqualTo("string")
      //opDogParamPut.getConsumes.asScala.toList must beEqualTo(List("application/json", "application/xml"))
      //opDogParamPut.getProduces.asScala.toList must beEqualTo(List("application/json", "application/xml"))
      opDogParamPut.getResponses.get("default").getContent.asScala.toList.head._2.getSchema.getType must beEqualTo("string")

      val catDef = api.get.getComponents.getSchemas.get("Cat")
      catDef.getType must beEqualTo("object")
      catDef.getProperties.containsKey("id") must beTrue
      catDef.getProperties.containsKey("name") must beTrue

      val dogDef = api.get.getComponents.getSchemas.get("Dog")
      dogDef.getType must beEqualTo("object")
      dogDef.getProperties.containsKey("id") must beTrue
      dogDef.getProperties.containsKey("name") must beTrue
    }
  }

  def toJsonString(data: Any): String = {
    if (data.getClass.equals(classOf[String])) {
      data.asInstanceOf[String]
    } else {
      Json.pretty(data.asInstanceOf[AnyRef])
    }
  }
}
