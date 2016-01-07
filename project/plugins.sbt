// These should match the version in build.sbt.
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.0")
addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")

// for testing sbt plugins:
libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
