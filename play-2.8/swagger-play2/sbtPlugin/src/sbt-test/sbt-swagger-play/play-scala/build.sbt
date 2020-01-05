organization in ThisBuild := "com.example"

version in ThisBuild := "1.0-SNAPSHOT"

scalaVersion in ThisBuild := "2.13.1"

libraryDependencies in ThisBuild += guice
libraryDependencies in ThisBuild += "io.swagger.core.v3" % "swagger-annotations" % "2.1.0"

lazy val module1 = (project in file("module1"))
  .enablePlugins(PlayScala)

lazy val module2 = (project in file("module2"))
  .enablePlugins(PlayScala)

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .dependsOn(module1, module2)
  .aggregate(module1, module2)