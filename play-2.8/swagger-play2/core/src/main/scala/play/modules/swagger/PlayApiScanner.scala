package play.modules.swagger

import scala.collection.JavaConverters._
//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging._
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.integration.SwaggerConfiguration
import io.swagger.v3.oas.integration.api.{OpenAPIConfiguration, OpenApiScanner}
import play.api.Environment
import play.routes.compiler.Route

/**
 * Identifies Play Controllers annotated as Swagger API's.
 * Uses the Play Router to identify Controllers, and then tests each for the API annotation.
 */
class PlayApiScanner (swaggerConfig: PlaySwaggerConfig, route: RouteWrapper, environment: Environment) extends OpenApiScanner {
  private[this] val logger = Logger[PlayApiScanner]
  private[this] var config: OpenAPIConfiguration = new SwaggerConfiguration()

  override def setConfiguration (config: OpenAPIConfiguration): Unit = {
    this.config = config
  }

  override def classes(): java.util.Set[Class[_]] = {
    logger.debug("ControllerScanner - looking for controllers with API annotation")

    val ignoredRoutes: scala.collection.Seq[String] = Option(config.getIgnoredRoutes).map(_.asScala.toSeq).getOrElse(Seq.empty[String]) ++ swaggerConfig.ignoreRoutes

    val routes0 = route.router
    val routes1 =
      if (swaggerConfig.onlyRoutes.isEmpty)
        routes0
      else
        routes0.filter({ case (_, route) => startsWith(swaggerConfig.onlyRoutes, swaggerConfig.basePath + route.path.toString) })

   val routes2 =
     if (ignoredRoutes.isEmpty)
       routes1
     else
       routes1.filter({ case (_, route) => !startsWith(ignoredRoutes, swaggerConfig.basePath + route.path.toString) })

    // get controller names from application routes
    val controllers: List[String] =
      routes2.map {
        case (_, route) =>
          if (route.call.packageName.isDefined) {
            s"${route.call.packageName.get}.${route.call.controller}"
          } else {
            route.call.controller
          }
      }.toList.distinct

    // Do not load hidden class
    val list = controllers.collect {
      case className: String if {
        try {
          environment.classLoader.loadClass(className).getAnnotation(classOf[Hidden]) == null
        } catch {
          case ex: Exception => {
            logger.error("Problem loading class:  %s. %s: %s".format(className, ex.getClass.getName, ex.getMessage))
            false
          }
        }
      } =>
        logger.debug("Found API controller:  %s".format(className))
        environment.classLoader.loadClass(className)
    }

    list.toSet.asJava
  }

  private def startsWith(list: scala.collection.Seq[String], path: String): Boolean = {
    for(str <- list){
      if (path.startsWith(str)) {
        return true
      }
    }
    false
  }

  override def resources(): java.util.Map[String, AnyRef] = Map.empty[String, AnyRef].asJava
}
