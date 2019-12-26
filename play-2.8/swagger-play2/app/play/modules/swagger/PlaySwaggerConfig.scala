package play.modules.swagger

import play.api.Configuration

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
  val apiVersion = config.get[String]("swagger.api.version")

  val basePath = config.get[String]("swagger.api.basepath")

  val host = config.get[String]("swagger.api.host")

  val title = config.get[String]("swagger.api.info.title")

  val description = config.get[String]("swagger.api.info.description")

  val termsOfServiceUrl = config.get[String]("swagger.api.info.termsOfService")

  val contact = config.get[String]("swagger.api.info.contact")

  val license = config.get[String]("swagger.api.info.license")

  // licenceUrl needs to be a valid URL to validate against schema
  val licenseUrl = config.get[String]("swagger.api.info.licenseUrl")

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
