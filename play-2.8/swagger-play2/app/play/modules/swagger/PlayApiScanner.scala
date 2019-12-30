package play.modules.swagger

//import io.swagger.annotations.Api
//import io.swagger.config._
//import io.swagger.models.{Contact, Info, License, Scheme, Swagger}
import java.util

import org.apache.commons.lang3.StringUtils
import com.typesafe.scalalogging._
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.integration.SwaggerConfiguration
import io.swagger.v3.oas.integration.api.{OpenAPIConfiguration, OpenApiScanner}
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.{Contact, Info, License}
import play.modules.swagger.util.SwaggerContext

import scala.jdk.CollectionConverters._
import javax.inject.Inject

/**
 * Identifies Play Controllers annotated as Swagger API's.
 * Uses the Play Router to identify Controllers, and then tests each for the API annotation.
 */
class PlayApiScanner @Inject() (ctx: SwaggerContext, playSwaggerConfig: PlaySwaggerConfig, route: RouteWrapper) extends OpenApiScanner {
  private[this] val logger = Logger[PlayApiScanner]
  private[this] var config: OpenAPIConfiguration = new SwaggerConfiguration()

  def updateInfoFromConfig(api: OpenAPI): Unit = {
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
    api.info(info)
  }

  override def setConfiguration (config: OpenAPIConfiguration): Unit = {
    //if (playSwaggerConfig.schemes != null) {
    //  for (s <- playSwaggerConfig.schemes) swagger.scheme(Scheme.forValue(s))
    //}
    //updateInfoFromConfig()
    //swagger.host(playSwaggerConfig.host)
    //swagger.basePath(playSwaggerConfig.basePath)

    this.config = config
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

    implicit val acceptablePackges = Option(config.getResourcePackages).map(_.asScala.toSet).getOrElse(Set.empty[String])

    val filterControllers: List[String] =
      if (acceptablePackges.isEmpty) {
        controllers
      } else {
        controllers.filter(cls => isAcceptable(cls))
      }

    // Do not load hidden class
    val list = filterControllers.collect {
      case className: String if {
        try {
          ctx.loadClass(className).getAnnotation(classOf[Hidden]) == null
        } catch {
          case ex: Exception => {
            logger.error("Problem loading class:  %s. %s: %s".format(className, ex.getClass.getName, ex.getMessage))
            false
          }
        }
      } =>
        logger.debug("Found API controller:  %s".format(className))
        ctx.loadClass(className)
    }

    list.toSet.asJava
  }

  private def isAcceptable(cls: String)(implicit acceptablePackges: Set[String]): Boolean = {
    for(pkg <- acceptablePackges) {
      if (cls.startsWith(pkg)) {
        return true
      }
    }
    false
  }

  override def resources(): util.Map[String, AnyRef] = Map.empty[String, AnyRef].asJava
}
