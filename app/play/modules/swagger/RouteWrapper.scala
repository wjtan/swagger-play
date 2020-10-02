package play.modules.swagger;

import play.api.Application
import play.api.routing.Router
import play.routes.compiler.{ Include, Route, RoutesFileParser, StaticPart }
import org.apache.commons.lang3.StringUtils

import scala.io.Source

import java.io.File
import javax.inject.{ Inject, Provider, Singleton }
import com.typesafe.scalalogging._

class RouteWrapper(val router: Map[String, Route]) {

  def apply(routeName: String): Route = router(routeName)

  def get(routeName: String) = router.get(routeName)

  def exists(routeName: String) = router.contains(routeName)

}

@Singleton
class RouteProvider @Inject() (router: Router, app: Application) extends Provider[RouteWrapper] {
  private[this] val logger = Logger[RouteProvider]

  import StringUtils._

  val config = app.configuration
  val routes: List[Route] = parseRoutes

  def parseRoutes: List[Route] = {
    def playRoutesClassNameToFileName(className: String) = className.replace(".Routes", ".routes")

    val routesFile = config.underlying.hasPath("play.http.router") match {
      case false => "routes"
      case true =>
        if (config.has("play.http.router")) {
          config.get[String]("play.http.router") match {
            case value if isEmpty(value) => "routes"
            case value => playRoutesClassNameToFileName(value)
          }
        } else {
          "routes"
        }
    }
    //Parses multiple route files recursively
    def parseRoutesHelper(routesFile: String, prefix: String): List[Route] = {
      logger.debug(s"Processing route file '$routesFile' with prefix '$prefix'")

      val resourceStream = app.classloader.getResourceAsStream(routesFile)
      if (resourceStream == null) {
        logger.debug(s"Cannot find $routesFile from classpath")
        return List.empty
      }

      val routesContent = Source.fromInputStream(resourceStream).mkString
      val parsedRoutes = RoutesFileParser.parseContent(routesContent, new File(routesFile))
      val routes = parsedRoutes.right.get.collect {
        case (route: Route) => {
          logger.debug(s"Adding route '$route'")
          Seq(route.copy(path = route.path.copy(parts = StaticPart(prefix) +: route.path.parts)))
        }
        case (include: Include) => {
          logger.debug(s"Processing route include $include")
          parseRoutesHelper(playRoutesClassNameToFileName(include.router), include.prefix)
        }
      }.flatten

      logger.debug(s"Finished processing route file '$routesFile'")
      routes
    }
    parseRoutesHelper(routesFile, "")
  }

  val routesRules = Map(routes map
    { route =>
      {
        val routeName = s"${route.call.packageName}.${route.call.controller}$$.${route.call.method}"
        (routeName -> route)
      }
    }: _*)

  val route = new RouteWrapper(routesRules)

  override def get() = route
}
