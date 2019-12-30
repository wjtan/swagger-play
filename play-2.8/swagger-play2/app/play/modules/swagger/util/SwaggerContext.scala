package play.modules.swagger.util

import io.swagger.v3.core.filter.OpenAPISpecFilter
import javax.inject.Singleton

import collection.mutable.ListBuffer
import org.slf4j.{Logger, LoggerFactory}

/**
  * @author ayush
  * @since 10/9/11 5:36 PM
  *
  */

@Singleton
class SwaggerContext {
  private val LOGGER = LoggerFactory.getLogger("play.modules.swagger.util.SwaggerContext")

  var suffixResponseFormat = true

  private val classLoaders = ListBuffer.empty[ClassLoader]
  registerClassLoader(this.getClass.getClassLoader)

  private var _filter: OpenAPISpecFilter = null

  def registerClassLoader(cl: ClassLoader) = this.classLoaders += cl

  def registerFilter(filter: OpenAPISpecFilter): Unit = {
    _filter = filter
  }

  def filter: Option[OpenAPISpecFilter] = Option(_filter)

  def loadClass(name: String) = {
    var clazz: Class[_] = null

    for (classLoader <- classLoaders.reverse) {
      if(clazz == null) {
        try {
          clazz = Class.forName(name, true, classLoader)
        } catch {
          case e: ClassNotFoundException => LOGGER.debug("Class not found in classLoader " + classLoader)
        }
      }
    }

    if(clazz == null)
      throw new ClassNotFoundException("class " + name + " not found")

    clazz
  }
}
