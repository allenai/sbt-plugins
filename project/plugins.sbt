resolvers += Resolver.url("bintray-sbt-plugin-releases",
  url("http://dl.bintray.com/content/sbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

resolvers += Resolver.url("bintray-allenai-sbt-plugin-releases",
  url("http://dl.bintray.com/content/allenai/sbt-plugins"))(Resolver.ivyStylePatterns)

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.1.2")

addSbtPlugin("org.allenai.plugins" % "allenai-sbt-format" % "2014.07.03-0")

addSbtPlugin("org.allenai.plugins" % "allenai-sbt-release" % "2014.07.03-0")
