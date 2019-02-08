package play.modules.swagger

import io.swagger.annotations.Api
import io.swagger.config._
import io.swagger.models.{ Contact, Info, License, Scheme, Swagger }
import org.apache.commons.lang3.StringUtils
import com.typesafe.scalalogging._
import play.modules.swagger.util.SwaggerContext
import scala.collection.JavaConverters._
import javax.inject.Inject

/**
 * Identifies Play Controllers annotated as Swagger API's.
 * Uses the Play Router to identify Controllers, and then tests each for the API annotation.
 */
class PlayApiScanner @Inject() (playSwaggerConfig: PlaySwaggerConfig, route: RouteWrapper) extends Scanner with SwaggerConfig {
  private[this] val logger = Logger[PlayApiScanner]

  private def updateInfoFromConfig(swagger: Swagger): Swagger = {
    val info = new Info()

    if (StringUtils.isNotBlank(playSwaggerConfig.description)) {
      info.description(playSwaggerConfig.description);
    }

    if (StringUtils.isNotBlank(playSwaggerConfig.title)) {
      info.title(playSwaggerConfig.title);
    } else {
      // title tag needs to be present to validate against schema
      info.title("");
    }

    if (StringUtils.isNotBlank(playSwaggerConfig.version)) {
      info.version(playSwaggerConfig.version);
    }

    if (StringUtils.isNotBlank(playSwaggerConfig.termsOfServiceUrl)) {
      info.termsOfService(playSwaggerConfig.termsOfServiceUrl);
    }

    if (playSwaggerConfig.contact != null) {
      info.contact(new Contact()
        .name(playSwaggerConfig.contact));
    }
    if (playSwaggerConfig.license != null && playSwaggerConfig.licenseUrl != null) {
      info.license(new License()
        .name(playSwaggerConfig.license)
        .url(playSwaggerConfig.licenseUrl));
    }
    swagger.info(info)
  }

  override def configure(swagger: Swagger): Swagger = {
    if (playSwaggerConfig.schemes != null) {
      for (s <- playSwaggerConfig.schemes) swagger.scheme(Scheme.forValue(s))
    }
    updateInfoFromConfig(swagger)
    swagger.host(playSwaggerConfig.host)
    swagger.basePath(playSwaggerConfig.basePath);

  }

  override def getFilterClass(): String = {
    null
  }

  override def classes(): java.util.Set[Class[_]] = {
    logger.debug("ControllerScanner - looking for controllers with API annotation")

    val routes = route.router.toList

    // get controller names from application routes
    val controllers = routes.map {
      case (_, route) =>
        if (route.call.packageName.isDefined) {
          s"${route.call.packageName.get}.${route.call.controller}"
        } else {
          route.call.controller
        }
    }.distinct

    val list = controllers.collect {
      case className: String if {
        try {
          SwaggerContext.loadClass(className).getAnnotation(classOf[Api]) != null
        } catch {
          case ex: Exception => {
            logger.error("Problem loading class:  %s. %s: %s".format(className, ex.getClass.getName, ex.getMessage))
            false
          }
        }
      } =>
        logger.debug("Found API controller:  %s".format(className))
        SwaggerContext.loadClass(className)
    }

    list.toSet.asJava
  }

  override def getPrettyPrint(): Boolean = {
    true;
  }

  override def setPrettyPrint(x: Boolean) {}
}
