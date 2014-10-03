import sbt._
import sbt.Keys._

import org.allenai.sbt.nodejs.NodeJsPlugin.autoImport.{ NodeKeys, Npm }
import org.allenai.sbt.webapp.WebappPlugin

// We have to define the build in Build.scala due to SBT bug caused by
// (seemingly) a dependency on scalariform or scalastyle in our StylePlugin project.
// TODO(markschaake): file a ticket with SBT about this
object TesterBuild extends Build {
  lazy val tester = project.in(file("."))
    .enablePlugins(WebappPlugin)
    .settings(NodeKeys.nodeProjectDir in Npm := file("client"))
}
