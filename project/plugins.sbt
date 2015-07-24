// These should match the version in build.sbt.
addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.0")
addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")

// for testing sbt plugins:
libraryDependencies <+= (sbtVersion) { sv =>
  "org.scala-sbt" % "scripted-plugin" % sv
}

resolvers += "Templemore Repository" at "http://templemore.co.uk/repo"

addSbtPlugin("templemore" % "sbt-cucumber-plugin" % "0.8.0")

dependencyOverrides += "org.scala-sbt" % "sbt" % "0.13.7"
