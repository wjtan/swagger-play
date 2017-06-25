package play.modules.swagger

import play.api.Configuration
import play.modules.swagger.util.ConfigUtil._

import javax.inject.{ Inject, Provider, Singleton }

case class PlaySwaggerConfig(
  schemes: Array[String] = null,
  title: String = "",
  version: String = "",
  description: String = "",
  termsOfServiceUrl: String = "",
  contact: String = "",
  license: String = "",
  licenseUrl: String = "",
  filterClass: String = null,
  host: String = "",
  basePath: String = "")

@Singleton
class PlaySwaggerConfigProvider @Inject() (implicit config: Configuration) extends Provider[PlaySwaggerConfig] {
  val apiVersion = getConfigString("api.version", "beta")

  val basePath = getConfigString("swagger.api.basepath", "/")

  val host = getConfigString("swagger.api.host", "localhost:9000")

  val title = getConfigString("swagger.api.info.title")

  val description = getConfigString("swagger.api.info.description")

  val termsOfServiceUrl = getConfigString("swagger.api.info.termsOfServiceUrl")

  val contact = getConfigString("swagger.api.info.contact")

  val license = getConfigString("swagger.api.info.license")

  // licenceUrl needs to be a valid URL to validate against schema
  val licenseUrl = getConfigString("swagger.api.info.licenseUrl", "http://licenseUrl")

  val swaggerConfig = PlaySwaggerConfig(
    null,
    title,
    apiVersion,
    description,
    termsOfServiceUrl,
    contact,
    license,
    licenseUrl,
    null,
    host,
    basePath)

  def get() = swaggerConfig
}
