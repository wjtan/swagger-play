package play.modules.swagger

import io.swagger.v3.oas.models.OpenAPI

import scala.collection.JavaConverters._
//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging._

class ApiListingCache(reader: PlayReader, scanner: PlayApiScanner) {
  private[this] val logger = Logger[ApiListingCache]

  private val cache: collection.mutable.Map[String, OpenAPI] = collection.mutable.Map.empty

  def listing(host: String): OpenAPI = {
    cache.getOrElseUpdate(host, {
      logger.debug("Loading API metadata")

      val classes = scanner.classes().asScala.toList
      reader.readSwaggerConfig()
      reader.read(classes)
    })
  }
}
