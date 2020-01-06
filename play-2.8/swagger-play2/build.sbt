ThisBuild / organization := "sg.wjtan"
//scalaVersion in ThisBuild := "2.13.1"

val coreVersion = "2.0.0-SNAPSHOT"

val PlayVersion = "2.8.0"
val SwaggerVersion = "2.1.0"
val Specs2Version = "4.8.1"

//mappings in (Compile, packageBin) ~= { _.filter(!_._1.getName.equals("logback-test.xml")) }

//publishTo <<= version { (v: String) =>
//  val nexus = "https://oss.sonatype.org/"
//  if (v.trim.endsWith("SNAPSHOT"))
//    Some("snapshots" at nexus + "content/repositories/snapshots")
//  else
//    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
//}

lazy val root = project.in(file("."))
  .aggregate(swaggerPlay, sbtSwaggerPlay)
  .settings(
    sourcesInBase := false,

    checksums in update := Nil,

    bintrayRepository := "maven",
    bintrayPackage := "swagger-play2",
    publishTo := Some("Artifactory Realm" at "https://oss.jfrog.org/artifactory/oss-snapshot-local;build.timestamp=" + new java.util.Date().getTime),
    bintrayReleaseOnPublish := false,

    licenses +=("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")),

    publishArtifact in Test := false,
    publishMavenStyle := true,
    pomIncludeRepository := { x => false },
    credentials += Credentials(Path.userHome / ".bintray" / ".artifactory")
  )
//  .settings(noPublishSettings:_*)

lazy val swaggerPlay = project.in(file("core"))
  .settings(
    name := "swagger-play2",
    version := coreVersion,
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play"                       % PlayVersion,
      "com.typesafe.play" %% "routes-compiler"            % PlayVersion,

      "org.slf4j"          % "slf4j-api"                  % "1.7.30",
      
      "com.typesafe.scala-logging" %% "scala-logging"     % "3.9.2",
      "io.swagger.core.v3" % "swagger-core"               % SwaggerVersion,
      "io.swagger.core.v3" % "swagger-integration"        % SwaggerVersion,
      "io.swagger.core.v3" % "swagger-jaxrs2"             % SwaggerVersion,
      "javax.ws.rs" % "javax.ws.rs-api" % "2.1.1",
      "com.fasterxml.jackson.module"  %% "jackson-module-scala"       % "2.10.1",
      //"io.swagger"        %% "swagger-scala-module"       % "1.0.6",
      "com.github.swagger-akka-http"  %% "swagger-scala-module"       % "2.0.5",

      "org.slf4j"          % "slf4j-simple"              % "1.7.30"            % Test,
      "com.typesafe.play" %% "play-guice"                 % PlayVersion        % Test,
      "com.typesafe.play" %% "play-ebean"                 % "5.0.2"            % Test,
      "org.specs2"        %% "specs2-core"                % Specs2Version      % Test,
      "org.specs2"        %% "specs2-mock"                % Specs2Version      % Test,
      "org.specs2"        %% "specs2-junit"               % Specs2Version      % Test,
      "org.mockito"        % "mockito-core"               % "3.2.0"            % Test),

    parallelExecution in Test := false, // Swagger uses global state which breaks parallel tests

    // Removed License from pomExtra to avoid "failed with status code 409: Conflict" error
    pomExtra := {
      <url>http://swagger.io</url>
      <scm>
        <url>git@github.com:swagger-api/swagger-play.git</url>
        <connection>scm:git:git@github.com:swagger-api/swagger-play.git</connection>
      </scm>
      <developers>
        <developer>
          <id>fehguy</id>
          <name>Tony Tam</name>
          <email>fehguy@gmail.com</email>
        </developer>
        <developer>
          <id>ayush</id>
          <name>Ayush Gupta</name>
          <email>ayush@glugbot.com</email>
        </developer>
        <developer>
          <id>rayyildiz</id>
          <name>Ramazan AYYILDIZ</name>
          <email>rayyildiz@gmail.com</email>
        </developer>
        <developer>
          <id>benmccann</id>
          <name>Ben McCann</name>
          <url>http://www.benmccann.com/</url>
        </developer>
        <developer>
          <id>frantuma</id>
          <name>Francesco Tumanischvili</name>
          <url>http://www.ft-software.net/</url>
        </developer>
        <developer>
          <id>wjtan</id>
          <name>Tan Wen Jun</name>
        </developer>
      </developers>
    }
  )


lazy val sbtSwaggerPlay = project.in(file("sbtPlugin"))
  .enablePlugins(SbtPlugin, ScriptedPlugin)
  .settings(
    addSbtPlugin("com.typesafe.sbt" %% "sbt-native-packager" % "1.5.2" % Provided),
    addSbtPlugin("com.typesafe.sbt" %% "sbt-web" % "1.4.4" % Provided))
  .settings(
    name := "sbt-swagger-play",
    version := "0.1-SNAPSHOT",
    sbtPlugin := true,

    libraryDependencies ++= Seq(
      "io.swagger.core.v3" % "swagger-core" % SwaggerVersion,
      organization.value %% "swagger-play2" % coreVersion,
    ),

    scripted := scripted.dependsOn(publishLocal in swaggerPlay).evaluated,
    scriptedLaunchOpts ++= Seq("-Xmx1024M", "-Dplugin.version=" + version.value),
    scriptedBufferLog := false
  )