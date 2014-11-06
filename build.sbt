import bintray.{ Keys => BintrayKeys }
import bintray.{ Plugin => BintrayPlugin }

libraryDependencies ++= Seq("com.typesafe" % "config" % "1.2.0")

organization := "org.allenai.plugins"

name := "plugins"

lazy val plugins = project.in(file(".")).enablePlugins(AllenaiReleasePlugin)

scalacOptions := Seq(
  "-encoding", "utf8",
  "-feature",
  "-unchecked",
  "-deprecation",
  "-language:_",
  "-Xlog-reflective-calls")

scalaVersion := "2.10.4"

sbtPlugin := true

conflictManager := ConflictManager.strict

// This is important because we wrap plugins that use a pre 0.13.5 SBT version
// (sbt-release, sbt-scalariform, sbt-scalastyle) which will cause loss of 0.13.5 sbt interface
// during SBT project load (this seems to be an SBT bug).
// This manifests as an SBT error complaining that `enablePlugins` is not a member
// of sbt.Project. To prevent this build error, we force those plugins to use 0.13.6
dependencyOverrides += "org.scala-sbt" % "sbt" % "0.13.6"

// We wrap some 3rd party plugins:
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.7.6")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.8.5")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.6.0")

addSbtPlugin("com.danieltrinh" % "sbt-scalariform" % "1.3.0")

// Wrapped by WebServicePlugin and WebappPlugin
addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")

// Allows us to test our plugins via the sbt-scripted plugin:
scriptedSettings

BintrayPlugin.bintrayPublishSettings

publishMavenStyle := false

BintrayKeys.repository in BintrayKeys.bintray := "sbt-plugins"

BintrayKeys.bintrayOrganization in BintrayKeys.bintray := Some("allenai")

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))

// TODO(markschaake): sbtCliApp and other archetype plugins
