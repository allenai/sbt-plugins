libraryDependencies ++= Seq(
  "org.webjars" % "mocha" % "1.17.1"
)

resolvers += "AllenAI Releases" at "http://utility.allenai.org:8081/nexus/content/repositories/releases"

credentials += Credentials("Sonatype Nexus Repository Manager",
  "utility.allenai.org",
  "deployment",
  "answermyquery")

addSbtPlugin("com.typesafe.sbt" % "sbt-js-engine" % "2014.3.18")
