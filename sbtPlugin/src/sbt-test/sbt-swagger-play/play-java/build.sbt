name := """play-java"""
organization := "com.example"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava, SwaggerPlayPlugin)

scalaVersion := "2.13.1"

libraryDependencies += guice
libraryDependencies += "io.swagger.core.v3" % "swagger-annotations" % "2.1.0"
