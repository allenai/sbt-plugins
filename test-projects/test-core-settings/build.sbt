import Dependencies._

name := "core-settings-tester"

// The core settings set the scala version for us, which will set it to:
//scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  jodaTime, // declared in Dependencies
  sprayCan, // declared in CoreDependencies
  allenAiCommon, // delcared in CoreDependencies, uses resolver in CoreSettings.
  sprayRouting,
  akkaActor,
  sprayJson)

ScaladocGenPlugin.fixNullCurrentBranch
