addSbtPlugin("me.lessis" % "bintray-sbt" % "0.1.2")

addSbtPlugin("org.allenai.plugins" % "allenai-sbt-style" % "2014.11.05-0")

addSbtPlugin("org.allenai.plugins" % "allenai-sbt-release" % "2014.11.05-0")

// for testing sbt plugins:
libraryDependencies <+= (sbtVersion) { sv =>
  "org.scala-sbt" % "scripted-plugin" % sv
}

conflictManager := ConflictManager.strict

dependencyOverrides ++= Set(
  "org.scala-sbt" % "sbt" % "0.13.7-RC3",
  "org.scala-sbt" % "classpath" % "0.13.7-RC3",
  "org.json4s" %% "json4s-core" % "3.2.10",
  "org.json4s" %% "json4s-native" % "3.2.10"
)
