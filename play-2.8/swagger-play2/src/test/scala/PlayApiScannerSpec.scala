import java.io.File

import play.modules.swagger._
import org.junit.runner.RunWith
import org.specs2.mutable._
import org.specs2.mock.Mockito
import org.specs2.runner.JUnitRunner
import play.api.Environment

import scala.jdk.CollectionConverters._
import play.routes.compiler.{Route => PlayRoute}

@RunWith(classOf[JUnitRunner])
class PlayApiScannerSpec extends Specification with Mockito {

  // set up mock for Play Router
  val routesList = {
    play.routes.compiler.RoutesFileParser.parseContent("""
GET /api/dog testdata.DogController.list
PUT /api/dog testdata.DogController.add1
GET /api/cat @testdata.CatController.list
PUT /api/cat @testdata.CatController.add1
GET /api/fly testdata.FlyController.list
PUT /api/dog testdata.DogController.add1
PUT /api/dog/:id testdata.DogController.add0(id:String)
GET /api/del/list testdata.DelegatedController.list
GET /del/list testdata.DelegatedController.list2
""", new File("")).getOrElse(List.empty).collect {
      case (route: PlayRoute) => {
        route
      }
    }
  }

  val config = PlaySwaggerConfig(
    description = "description",
    basePath = "/",
    contact = "contact",
    host = "127.0.0.1",
    version = "beta",
    title = "title",
    termsOfServiceUrl = "http://termsOfServiceUrl",
    license = "license",
    licenseUrl = "http://licenseUrl",
    filterClass = None,
    ignoreRoutes = List("/api/del"), // Ignore list
    onlyRoutes = List("/api") // Ignore list2
  )

  val routesRules = SwaggerPluginHelper.buildRouteRules(routesList)
  val route = new RouteWrapper(routesRules)
  val env = Environment.simple()
  val scanner = new PlayApiScanner(config, route, env)


  "PlayApiScanner" should {
    "identify correct API classes based on router and API annotations" in {
      val classes = scanner.classes()

      classes.asScala.toList.length must beEqualTo(2)
      classes.contains(env.classLoader.loadClass("testdata.DogController")) must beTrue
      classes.contains(env.classLoader.loadClass("testdata.CatController")) must beTrue
    }
  }

}
