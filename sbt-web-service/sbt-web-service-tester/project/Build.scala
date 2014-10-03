import sbt._
import sbt.Keys._

import org.allenai.sbt.webservice.WebServicePlugin

object TesterBuild extends Build {
  lazy val tester = project.in(file(".")).enablePlugins(WebServicePlugin)
}
