package sg.wjtan.sbtSwaggerPlay

import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
import com.typesafe.sbt.web.Import._

import io.swagger.v3.core.util.Json // package conflict with sbt.io
import play.modules.swagger._

import scala.collection.JavaConverters._
import java.nio.file.{ Files, Paths, StandardOpenOption }

import sbt._
import sbt.Keys._
import sbt.plugins.JvmPlugin


object SwaggerPlayPlugin extends AutoPlugin {

  override def trigger = noTrigger
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
    val swaggerRouteFile = settingKey[File]("Location of route file")
    val swaggerTarget = settingKey[File]("the location of the swagger documentation in your packaged app.")
    val swaggerFilename = settingKey[String]("the swagger filename the swagger documentation in your packaged app")
    val swagger = TaskKey[Unit]("swagger", "Generate swagger.json")
  }

  val swaggerConfig = TaskKey[PlaySwaggerConfig]("swagger-config")

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
    swaggerTarget := file("public"),
    swaggerFilename := "swagger.json",
    swaggerConfig := PlaySwaggerConfig(
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
    ),
    swagger := Def.taskDyn { SwaggerPlay.generateTask(swaggerConfig.value, swaggerRouteFile.value, swaggerTarget.value / swaggerFilename.value, streams.value) }.value,
    unmanagedResourceDirectories in Assets += swaggerTarget.value,
    mappings in (Compile, packageBin) += {
      val file = swaggerTarget.value / swaggerFilename.value
      file -> file.toString
    }, //include it in the unmanagedResourceDirectories in Assets doesn't automatically include it package
    packageBin in Universal := (packageBin in Universal).dependsOn(swagger).value,
    run := (run in Compile).dependsOn(swagger).evaluated,
    stage := stage.dependsOn(swagger).value
  )

  //override lazy val buildSettings = Seq()

  //override lazy val globalSettings = Seq()
}

object SwaggerPlay {
  def generateTask(swaggerConfig: PlaySwaggerConfig, swaggerRouteFile: File, swaggerOutputFile: File, streams: TaskStreams): Def.Initialize[Task[Unit]] = Def.task {
    streams.log info s"[${name.value}] Generating Swagger"

    val classPath = (classDirectory in Compile).value.toURI.toURL
    val resourcePaths: List[URL] = (resourceDirectories in Compile).value.map(_.toURI.toURL).toList
    val dependencyPaths: List[URL] = (dependencyClasspath in Compile).value.map(_.data.toURI.toURL).toList
    val allPaths: List[URL] = resourcePaths ++ dependencyPaths :+ classPath

    // Parent loader as this ClassLoader
    implicit val classLoader = new java.net.URLClassLoader(allPaths.toArray, this.getClass.getClassLoader)

    val routeFile = swaggerRouteFile.toString
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
      val filename = Paths.get(swaggerOutputFile.toURI)
      streams.log debug "Writing to " + filename
      streams.log verbose json

      Files.write(filename, json.getBytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    } else {
      streams.log error "No routes"
    }
  }
}