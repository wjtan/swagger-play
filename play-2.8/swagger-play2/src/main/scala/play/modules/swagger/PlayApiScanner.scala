package play.modules.swagger

import java.util

import scala.jdk.CollectionConverters._

import com.typesafe.scalalogging._
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.integration.SwaggerConfiguration
import io.swagger.v3.oas.integration.api.{OpenAPIConfiguration, OpenApiScanner}

import play.api.Environment

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

    val routes = route.router.toList

    // get controller names from application routes
    val controllers: List[String] =
      if (swaggerConfig.onlyRoutes.nonEmpty) {
        swaggerConfig.onlyRoutes.toList
      } else {
        routes.map {
          case (_, route) =>
            if (route.call.packageName.isDefined) {
              s"${route.call.packageName.get}.${route.call.controller}"
            } else {
              route.call.controller
            }
        }.distinct
    }

    implicit val ignorePackges: Set[String] =
      Option(config.getIgnoredRoutes).map(_.asScala.toSet).getOrElse(Set.empty[String]) union swaggerConfig.ignoreRoutes.toSet

    val filterControllers: List[String] =
      if (ignorePackges.isEmpty) {
        controllers
      } else {
        controllers.filter(isIgnored)
      }

    // Do not load hidden class
    val list = filterControllers.collect {
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

  private def isIgnored(cls: String)(implicit ignoredPackges: Set[String]): Boolean = {
    for(pkg <- ignoredPackges) {
      if (cls.startsWith(pkg)) {
        return false
      }
    }
    true
  }

  override def resources(): util.Map[String, AnyRef] = Map.empty[String, AnyRef].asJava
}
