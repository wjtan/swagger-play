package play.modules.swagger

import io.swagger.config._
import io.swagger.models.Swagger

import javax.inject.{ Inject, Singleton }
import scala.collection.JavaConverters._
import com.typesafe.scalalogging._

@Singleton
class ApiListingCache @Inject() (reader: PlayReader) {
  private[this] val logger = Logger[ApiListingCache]

  var cache: Option[Swagger] = None

  def listing(docRoot: String, host: String): Option[Swagger] = {
    cache.orElse {
      logger.debug("Loading API metadata")

      val scanner = ScannerFactory.getScanner()
      val classes = scanner.classes()
      var swagger = reader.read(classes.asScala.toSet)

      scanner match {
        case config: SwaggerConfig => {
          swagger = config.configure(swagger)
        }
        case config => {
          // no config, do nothing
        }
      }
      cache = Some(swagger)
      cache
    }
    cache.get.setHost(host)
    cache
  }
}
