lazy val root = project
  .in(file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    Release.releaseSettings,
    name := "allenai-sbt-plugins",
    sbtPlugin := true,
    codeArtifactUrl := "https://org-allenai-s2-896129387501.d.codeartifact.us-west-2.amazonaws.com/maven/private",
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
