val nexusHost = "utility.allenai.org"

val nexus = s"http://${nexusHost}:8081/nexus/content/repositories/releases"

credentials += Credentials(
  "Sonatype Nexus Repository Manager",
  "utility.allenai.org",
  "deployment",
  "answermyquery")

resolvers += "AllenAI Releases" at nexus

resolvers += "Typesafe Releases Repository" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.typesafe.sbt" % "sbt-jshint" % "1.0.0-M2a")

addSbtPlugin("com.typesafe.sbt" % "sbt-less" % "1.0.0-M2a")
