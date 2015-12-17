resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.2.0",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
)

organization := "org.allenai.plugins"

name := "allenai-sbt-plugins"

lazy val ai2Plugins = project.in(file("."))

scalacOptions := Seq(
  "-encoding", "utf8",
  "-feature",
  "-unchecked",
  "-deprecation",
  "-language:_",

  "-Xlog-reflective-calls")

// SBT requires 2.10 for now (1/15/15).
scalaVersion := "2.10.4"

sbtPlugin := true

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.0")

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.0")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.8.0" excludeAll(
  // scalastyle depends on an old version of scalariform. We bring in the latest with the
  // sbt-scalariform plugin dependency below.
  ExclusionRule(organization = "com.danieltrinh")
))

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.6.0")

// Dependency graph visualiztion in SBT console
addSbtPlugin("com.gilt" % "sbt-dependency-graph-sugar" % "0.7.4")

// Wrapped by WebServicePlugin and WebappPlugin
addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")

// Allows us to test our plugins via the sbt-scripted plugin:
scriptedSettings


// Plugins for generating and publishing scaladoc.

// sbt-unidoc lets you bundle subproject scaladoc into a single place.
addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.3.3")
// sbt-site has tasks for publishing websites.
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "0.7.1")
// sbt-ghpages has tasks for publishing sbt-sites to github pages.
addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.5.2")


// Publication settings.

publishMavenStyle := false

bintrayRepository := "sbt-plugins"

bintrayOrganization := Some("allenai")

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))

