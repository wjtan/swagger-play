package sg.wjtan.sbtSwaggerPlay

import io.swagger.v3.core.util.Json

import scala.collection.JavaConverters._
import java.nio.file.{ Files, Paths, StandardOpenOption }
import play.modules.swagger._

import sbt._
import sbt.Keys._
import sbt.plugins.JvmPlugin

object SwaggerPlayPlugin extends AutoPlugin {

  override def trigger = allRequirements
  override def requires = JvmPlugin

  object autoImport {
    val exampleSetting = settingKey[String]("A setting that is automatically imported to the build")
    val swaggerTask = TaskKey[Unit]("swagger", "A task that is automatically imported to the build")
  }

  import autoImport._

  override lazy val projectSettings = Seq(
    exampleSetting := "just an example"
  ) ++ inConfig(Compile)(Seq(
    swaggerTask := Def.taskDyn { SwaggerPlay.generateTask(streams.value) }.value
  ))

  //override lazy val buildSettings = Seq()

  //override lazy val globalSettings = Seq()
}

object SwaggerPlay {
  def generateTask(streams: TaskStreams): Def.Initialize[Task[Unit]] = Def.task {
    //streams.log info s"Classes ${classDirectory.value}"
    println("Tasking")

    val classPath = classDirectory.value.toURI.toURL
    val resourcePaths: Array[URL] = resourceDirectories.value.map(_.toURI.toURL).toArray
    val dependencyPaths: Array[URL] = dependencyClasspath.value.map(_.data.toURI.toURL).toArray
    val allPaths: Array[URL] = resourcePaths ++ dependencyPaths :+ classPath

    // Parent loader as this ClassLoader
    implicit val classLoader = new java.net.URLClassLoader(allPaths, this.getClass.getClassLoader)

    val swaggerConfig = PlaySwaggerConfig.empty

    val routes = new RouteWrapper(SwaggerPluginHelper.buildRouteRules("routes", ""))
    if (routes.router.nonEmpty) {
      val scanner = new PlayApiScanner(swaggerConfig, routes, classLoader)
      val reader = new PlayReader(swaggerConfig, routes)

      val appClasses = scanner.classes().asScala.toList
      println("Scanned Classes: " + appClasses.size)

      reader.readSwaggerConfig()
      val api = reader.read(appClasses)
      val json = Json.pretty(api)
      val filename = Paths.get((target.value / "swagger.json").toURI())
      println("Writing to " + filename)
      println(json)

      Files.write(filename, json.getBytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    } else {
      println("No routes")
    }
  }
}