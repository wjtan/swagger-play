//addSbtPlugin("com.typesafe.play" % "sbt-plugin"      % "2.8.0")

libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.0.0")

//addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.2.4")

// publishing to bintray
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.4")