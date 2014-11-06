resolvers += Resolver.url("bintray-sbt-plugin-releases",
  url("http://dl.bintray.com/content/sbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.1.2")

addSbtPlugin("org.allenai.plugins" % "allenai-sbt-style" % "2014.11.05-0")

addSbtPlugin("org.allenai.plugins" % "allenai-sbt-release" % "2014.11.05-0")

libraryDependencies <+= (sbtVersion) { sv =>
  "org.scala-sbt" % "scripted-plugin" % sv
}
