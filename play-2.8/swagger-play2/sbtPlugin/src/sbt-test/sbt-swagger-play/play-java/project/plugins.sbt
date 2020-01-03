// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.0")

// Defines scaffolding (found under .g8 folder)
// http://www.foundweekends.org/giter8/scaffolding.html
// sbt "g8Scaffold form"
//addSbtPlugin("org.foundweekends.giter8" % "sbt-giter8-scaffold" % "0.11.0")

{
  val pluginVersion = System.getProperty("plugin.version")
  if(pluginVersion == null)
    throw new RuntimeException("""|The system property 'plugin.version' is not defined.
                                  |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
  else addSbtPlugin("sg.wjtan" % """sbt-swagger-play""" % pluginVersion)
}
