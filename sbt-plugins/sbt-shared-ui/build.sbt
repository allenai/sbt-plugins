val nexusHost = "utility.allenai.org"

val nexus = s"http://${nexusHost}:8081/nexus/content/repositories/snapshots"

credentials += Credentials(
  "Sonatype Nexus Repository Manager",
  "utility.allenai.org",
  "deployment",
  "answermyquery")

resolvers += "AllenAI Snapshots" at nexus

addSbtPlugin("com.typesafe.sbt" % "sbt-jshint-plugin" % "1.0.0-ai2-SNAPSHOT")

addSbtPlugin("com.typesafe.sbt" % "sbt-less-plugin" % "1.0.0-ai2-SNAPSHOT")
