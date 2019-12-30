package play.modules.swagger

import javax.inject.{Inject, Provider, Singleton}
import io.swagger.v3.oas.models.OpenAPI

import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging._

@Singleton
class ApiListingCache @Inject() (readerProvider: Provider[PlayReader], scanner: PlayApiScanner) {
  private[this] val logger = Logger[ApiListingCache]

  var cache: Option[OpenAPI] = None

  def listing(docRoot: String, host: String): Option[OpenAPI] = {
    cache.orElse {
      logger.debug("Loading API metadata")

      //val scanner = ScannerFactory.getScanner()
      //scanner.updateInfoFromConfig(api) // FIXME

      val classes = scanner.classes().asScala.toList
      val reader = readerProvider.get()
      val api: OpenAPI = reader.read(classes)

      //scanner match {
      //  case config: SwaggerConfig => {
      //    swagger = config.configure(swagger)
      //  }
      //  case _ => // no config, do nothing
      //}
      cache = Some(api)
      cache
    }
    //cache.get.setHost(host)
    cache
  }
}
