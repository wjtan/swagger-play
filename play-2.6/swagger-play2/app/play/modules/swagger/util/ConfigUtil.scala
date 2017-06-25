package play.modules.swagger.util

import play.api.Configuration
import org.apache.commons.lang3.StringUtils

object ConfigUtil {
  def getConfigString(path: String, default: String = "")(implicit config: Configuration): String = {
    if (config.has(path)) {
      val value = config.get[String](path)
      if (StringUtils.isEmpty(value)) {
        default
      } else {
        value
      }
    } else {
      default
    }
  }
}
