resolvers += Resolver.url("bintray-sbt-plugin-releases",
  url("http://dl.bintray.com/content/sbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.1.1")

resolvers ++= Seq(
  Resolver.sonatypeRepo("snapshots")
)

addSbtPlugin("org.allenai.plugins" % "sbt-format" % "2014.5.9-1-SNAPSHOT")
