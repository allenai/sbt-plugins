addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.3")

addSbtPlugin(
  ("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0").excludeAll(
    // scalastyle depends on an old version of scalariform. We bring in the latest with the
    // sbt-scalariform plugin dependency below.
    ExclusionRule(organization = "com.danieltrinh")
  )
)

// If you change the scalariform version, you may also need to generate a new
// scalariform.jar in src/main/resources/autoformat/
addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.3")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.13")

addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.6")

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    crossSbtVersions := Vector("1.3.10", "0.13.16"),
    resolvers += Resolver.jcenterRepo,
    libraryDependencies ++= Seq(
      "com.typesafe" % "config" % "1.4.0",
      "org.scalatest" %% "scalatest" % "3.1.2" % "test"
    ),
    organization := "org.allenai.plugins",
    name := "allenai-sbt-plugins",
    scalacOptions := Seq(
      "-encoding",
      "utf8",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-language:_",
      "-Xlog-reflective-calls"
    ),
    sbtPlugin := true,
    // Publication settings.
    publishMavenStyle := false,
    bintrayRepository := "sbt-plugins",
    bintrayOrganization := Some("allenai"),
    licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    // Allows us to test our plugins via the sbt-scripted plugin:
    // scriptedSettings,
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++ Seq(
        "-Xmx1024M",
        "-Dplugin.version=" + version.value
      )
    },
    // If we don't do this, the the scripted tests run without producing console output.
    scriptedBufferLog := false,
    // Hook in our scripted tests to the test command. This makes it so scripted tests must pass
    // for a release to be possible.
    test := {
      test.in(Test).value
      scripted.toTask("").value
    }
  )
