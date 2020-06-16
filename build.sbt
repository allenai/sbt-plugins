addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.3")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.13")

addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.6")

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    Release.releaseSettings,
    name := "allenai-sbt-plugins",
    sbtPlugin := true,
    resolvers += Resolver.jcenterRepo,
    libraryDependencies ++= Seq(
      "com.typesafe" % "config" % "1.4.0",
      "org.scalatest" %% "scalatest" % "3.1.2" % "test"
    ),
    scalacOptions := Seq(
      "-encoding",
      "utf8",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-language:_",
      "-Xlog-reflective-calls"
    ),
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
