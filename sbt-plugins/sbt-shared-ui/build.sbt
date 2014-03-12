val nexusHost = "utility.allenai.org"

val nexus = s"http://${nexusHost}:8081/nexus/content/repositories/releases"

credentials += Credentials(
  "Sonatype Nexus Repository Manager",
  "utility.allenai.org",
  "deployment",
  "answermyquery")

resolvers += "AllenAI Snapshots" at nexus

addSbtPlugin("com.typesafe.sbt" % "sbt-jshint-plugin" % "2014.3.12")

addSbtPlugin("com.typesafe.sbt" % "sbt-less-plugin" % "2014.3.12")
