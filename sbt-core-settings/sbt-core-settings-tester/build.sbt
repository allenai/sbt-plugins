import CoreSettings.Dependencies._

name := "core-settings-tester"

// The core settings set the scala version for us
//scalaVersion := "2.10.4"

// Since CoreSettingsPlugin.autoImport contains a CoreSettings object
// and since the plugin is automatically enabled, we can add dependencies
// defined in CoreSettings.Dependencies (imported above)
libraryDependencies ++= Seq(
  sprayCan,
  sprayRouting,
  akkaActor,
  sprayJson)
