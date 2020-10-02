name := "swagger-play2"
version := "1.7.0"

checksums in update := Nil

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  "com.fasterxml.jackson.module"  %% "jackson-module-scala"       % "2.9.8",
  "org.slf4j"          % "slf4j-api"                  % "1.7.25",
  "com.typesafe.scala-logging" %% "scala-logging"     % "3.9.0",
  "io.swagger"         % "swagger-core"               % "1.5.21",
  "io.swagger"        %% "swagger-scala-module"       % "1.0.4",
  "com.typesafe.play" %% "routes-compiler"            % "2.7.0",
  "com.typesafe.play" %% "play-ebean"                 % "5.0.1"            % "test",
  "org.specs2"        %% "specs2-core"                % "3.8.9"            % "test",
  "org.specs2"        %% "specs2-mock"                % "3.8.9"            % "test",
  "org.specs2"        %% "specs2-junit"               % "3.8.9"            % "test",
  "org.mockito"        % "mockito-core"               % "2.8.47"            % "test")

mappings in (Compile, packageBin) ~= { _.filter(!_._1.getName.equals("logback.xml")) }

//publishTo <<= version { (v: String) =>
//  val nexus = "https://oss.sonatype.org/"
//  if (v.trim.endsWith("SNAPSHOT"))
//    Some("snapshots" at nexus + "content/repositories/snapshots")
//  else
//    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
//}

bintrayRepository := "maven"

bintrayPackage := "swagger-play2"

// publishTo := Some("Artifactory Realm" at "https://oss.jfrog.org/artifactory/oss-snapshot-local;build.timestamp=" + new java.util.Date().getTime)
// bintrayReleaseOnPublish := false

licenses +=("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))

publishArtifact in Test := false
publishMavenStyle := true
pomIncludeRepository := { x => false }
credentials += Credentials(Path.userHome / ".bintray" / ".artifactory")
organization := "sg.wjtan"

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

lazy val root = (project in file(".")).enablePlugins(PlayScala)
