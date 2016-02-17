resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.2.0"
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
// TODO(danm): Upgrade Scala version, since this is no longer the case?
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

// If you change the scalariform version, you may also need to generate a new
// scalariform.jar in src/main/resources/autoformat/
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.6.0")

// Dependency graph visualiztion in SBT console
addSbtPlugin("com.gilt" % "sbt-dependency-graph-sugar" % "0.7.4")

// Wrapped by WebServicePlugin and WebappPlugin
addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")


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

// Allows us to test our plugins via the sbt-scripted plugin:

scriptedSettings

scriptedLaunchOpts := {
  scriptedLaunchOpts.value ++ Seq(
    "-Xmx1024M", "-Dplugin.version=" + version.value
  )
}

// If we don't do this, the the scripted tests run without producing console output.
scriptedBufferLog := false

// Hook in our scripted tests to the test command. This makes it so scripted tests must pass
// for a release to be possible.
test := {
  (test in Test).value
  scripted.toTask("").value
}
