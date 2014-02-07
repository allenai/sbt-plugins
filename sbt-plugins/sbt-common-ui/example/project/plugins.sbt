val nexusHost = "utility.allenai.org"

val nexus = s"http://${nexusHost}:8081/nexus/content/repositories/snapshots"

credentials += Credentials(
  "Sonatype Nexus Repository Manager",
  "utility.allenai.org",
  "deployment",
  "answermyquery")

resolvers += "AllenAI Snapshots" at nexus

addSbtPlugin("org.allenai.plugins" % "sbt-common-ui" % "0.2.3-SNAPSHOT")
