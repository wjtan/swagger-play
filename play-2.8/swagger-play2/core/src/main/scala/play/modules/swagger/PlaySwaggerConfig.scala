package play.modules.swagger

import com.typesafe.config.Config
import javax.annotation.Nullable
import play.api.Configuration

case class PlaySwaggerConfig(
                              title: String,
                              version: String,
                              description: String,
                              termsOfServiceUrl: String,
                              contact: String,
                              license: String,
                              licenseUrl: String,
                              host: String,
                              basePath: String,
                              ignoreRoutes: Seq[String],
                              onlyRoutes: Seq[String],
                              filterClass: Option[String]
                            ) {

  @Nullable def getFilterClass: String = filterClass.orNull
}

object PlaySwaggerConfig {
  def defaultReference: PlaySwaggerConfig = PlaySwaggerConfig(Configuration.reference)

  def apply(configuration: Configuration): PlaySwaggerConfig = {
    PlaySwaggerConfig(
      version = configuration.get[String]("swagger.api.version"),
      description = configuration.get[String]("swagger.api.info.description"),
      host = configuration.get[String]("swagger.api.host"),
      basePath = configuration.get[String]("swagger.api.basepath"),
      title = configuration.get[String]("swagger.api.info.title"),
      contact = configuration.get[String]("swagger.api.info.contact"),
      termsOfServiceUrl = configuration.get[String]("swagger.api.info.termsOfServiceUrl"),
      license = configuration.get[String]("swagger.api.info.license"),
      licenseUrl = configuration.get[String]("swagger.api.info.licenseUrl"),
      ignoreRoutes = configuration.get[Seq[String]]("swagger.api.ignoreRoutes"),
      onlyRoutes = configuration.get[Seq[String]]("swagger.api.onlyRoutes"),
      filterClass = configuration.get[Option[String]]("swagger.filter")
    )
  }

  def apply(config: Config): PlaySwaggerConfig = apply(Configuration(config))
}
