import org.specs2.mock.Mockito
import org.specs2.mutable._
import play.api.Environment
import play.modules.swagger._
import play.routes.compiler.Route

import scala.collection.JavaConverters._
//import scala.jdk.CollectionConverters._

class PlayDelegatedApiScannerSpec extends Specification with Mockito {

  val routes: List[Route] =
    SwaggerPluginHelper.parseRoutes("delegation", "/api", Environment.simple())


  val routesRules = SwaggerPluginHelper.buildRouteRules(routes)

  val swaggerConfig = PlaySwaggerConfig.defaultReference.copy(
    basePath = "/",
    host = "127.0.0.1"
  )

  val env = Environment.simple()
  val route = new RouteWrapper(routesRules)
  val scanner = new PlayApiScanner(swaggerConfig, route, env)
  val playReader = new PlayReader(swaggerConfig, route)
  val apiListingCache = new ApiListingCache( playReader, scanner)

  "route parsing" should {
    "separate delegated paths correctly" in {

      val urls = apiListingCache.listing("127.0.0.1").getPaths.keySet.asScala

      urls must contain("/api/all")
      urls must contain("/api/my/action")
      urls must contain("/api/subdelegated/all")
      urls must contain("/api/subdelegated/my/action")
      urls must contain("/api/subdelegated")
    }
  }

}
