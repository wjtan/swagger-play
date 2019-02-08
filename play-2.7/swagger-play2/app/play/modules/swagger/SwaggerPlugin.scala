/**
 * Copyright 2014 Reverb Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package play.modules.swagger

import javax.inject.Inject
import io.swagger.config.{ FilterFactory, ScannerFactory }
import play.modules.swagger.util.SwaggerContext
import io.swagger.core.filter.SwaggerSpecFilter
import play.api.inject.ApplicationLifecycle
import play.api.Application
import play.api.routing.Router
import scala.concurrent.Future
import scala.collection.JavaConversions._
import org.apache.commons.lang3.StringUtils
import com.typesafe.scalalogging._

trait SwaggerPlugin

class SwaggerPluginImpl @Inject() (lifecycle: ApplicationLifecycle, app: Application,
    cache: ApiListingCache, scanner: PlayApiScanner) extends SwaggerPlugin {

  import StringUtils._

  private[this] val logger = Logger[SwaggerPlugin]

  logger.debug("Swagger - starting initialisation...")

  SwaggerContext.registerClassLoader(app.classloader)

  ScannerFactory.setScanner(scanner)

  val config = app.configuration

  if (config.has("swagger.filter")) {
    config.get[String]("swagger.filter") match {
      case value if isEmpty(value) =>
      case e => {
        try {
          FilterFactory setFilter SwaggerContext.loadClass(e).newInstance.asInstanceOf[SwaggerSpecFilter]
          logger.debug("Setting swagger.filter to %s".format(e))
        } catch {
          case ex: Exception => logger.error(s"Failed to load filter $e", ex)
        }
      }
    }
  }

  val docRoot = ""
  cache.listing(docRoot, "127.0.0.1")

  logger.debug("Swagger - initialization done.")

  // previous contents of Plugin.onStart
  lifecycle.addStopHook { () =>
    cache.cache = None
    logger.debug("Swagger - stopped.")

    Future.successful(())
  }

}
