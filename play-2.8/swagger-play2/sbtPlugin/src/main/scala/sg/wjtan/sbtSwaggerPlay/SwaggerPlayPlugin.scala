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
    val swaggerApiVersion = settingKey[String]("Version of API")
    val swaggerDescription = settingKey[String]("Description")
    val swaggerHost = settingKey[String]("Host")
    val swaggerBasePath = settingKey[String]("Base url")
    val swaggerTitle = settingKey[String]("Title")
    val swaggerContact = settingKey[String]("Contact Information")
    val swaggerTermsOfServiceUrl = settingKey[Option[URL]]("Terms of Service URL")
    val swaggerLicense = settingKey[String]("License")
    val swaggerLicenseUrl = settingKey[Option[URL]]("License URL")
    val swaggerIgnoreRoutes = settingKey[Seq[String]]("Ignore routes")
    val swaggerAcceptRoutes = settingKey[Seq[String]]("Accept only routes. Empty to accept all routes.")
    val swaggerRouteFile = settingKey[File]("Location of swagger file")
    val swaggerOutputFile = settingKey[File]("Output path for swagger")
    val swaggerTask = TaskKey[Unit]("swagger", "Generate swagger.json")
  }

  import autoImport._

  override lazy val projectSettings = Seq(
    swaggerApiVersion := "beta",
    swaggerDescription := "",
    swaggerHost := "localhost:9000",
    swaggerBasePath := "/",
    swaggerTitle := "",
    swaggerContact := "",
    swaggerTermsOfServiceUrl := None,
    swaggerLicense := "",
    swaggerLicenseUrl := None,
    swaggerIgnoreRoutes := Seq.empty,
    swaggerAcceptRoutes := Seq.empty,
    swaggerRouteFile := file("routes"),
    swaggerOutputFile := file("public/swagger.json")
  ) ++ inConfig(Compile)(Seq(
    swaggerTask := Def.taskDyn { SwaggerPlay.generateTask(streams.value) }.value
  ))

  //override lazy val buildSettings = Seq()

  //override lazy val globalSettings = Seq()
}

object SwaggerPlay {
  import SwaggerPlayPlugin.autoImport._

  def generateTask(streams: TaskStreams): Def.Initialize[Task[Unit]] = Def.task {
    streams.log info "Generating Swagger"

    val classPath = classDirectory.value.toURI.toURL
    val resourcePaths: Array[URL] = resourceDirectories.value.map(_.toURI.toURL).toArray
    val dependencyPaths: Array[URL] = dependencyClasspath.value.map(_.data.toURI.toURL).toArray
    val allPaths: Array[URL] = resourcePaths ++ dependencyPaths :+ classPath

    // Parent loader as this ClassLoader
    implicit val classLoader = new java.net.URLClassLoader(allPaths, this.getClass.getClassLoader)

    val swaggerConfig = PlaySwaggerConfig(
      version = swaggerApiVersion.value,
      description = swaggerDescription.value,
      host = swaggerHost.value,
      basePath = swaggerBasePath.value,
      title = swaggerTitle.value,
      contact = swaggerContact.value,
      termsOfServiceUrl = swaggerTermsOfServiceUrl.value.map(_.toString).getOrElse(""),
      license = swaggerLicense.value,
      licenseUrl = swaggerLicenseUrl.value.map(_.toString).getOrElse(""),
      ignoreRoutes = swaggerIgnoreRoutes.value,
      onlyRoutes = swaggerAcceptRoutes.value,
      filterClass = None
    )

    val routeFile = swaggerRouteFile.value.toString
    streams.log debug "Reading Route " + routeFile
    val routes = new RouteWrapper(SwaggerPluginHelper.buildRouteRules(routeFile, ""))
    if (routes.router.nonEmpty) {
      val scanner = new PlayApiScanner(swaggerConfig, routes, classLoader)
      val reader = new PlayReader(swaggerConfig, routes)

      val appClasses = scanner.classes().asScala.toList
      streams.log debug "Scanned Classes: " + appClasses.size

      reader.readSwaggerConfig()
      val api = reader.read(appClasses)
      val json = Json.pretty(api)
      val filename = Paths.get(swaggerOutputFile.value.toURI)
      streams.log debug "Writing to " + filename
      println(json)

      Files.write(filename, json.getBytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    } else {
      streams.log info "No routes"
    }
  }
}