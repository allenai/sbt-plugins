addSbtPlugin("me.lessis" % "bintray-sbt" % "0.2.1")

// This is required until our allenai-sbt-plugins is added
// to https://bintray.com/sbt/sbt-plugin-releases (request pending)
resolvers += Resolver.url("bintray-allenai-sbt-plugin-releases",
  url("http://dl.bintray.com/content/allenai/sbt-plugins"))(Resolver.ivyStylePatterns)

addSbtPlugin("org.allenai.plugins" % "allenai-sbt-plugins" % "2015.02.16-0")

// for testing sbt plugins:
libraryDependencies <+= (sbtVersion) { sv =>
  "org.scala-sbt" % "scripted-plugin" % sv
}

dependencyOverrides += "org.scala-sbt" % "sbt" % "0.13.7"
