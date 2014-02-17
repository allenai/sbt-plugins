val nexusHost = "utility.allenai.org"

def nexus(releaseSnap: String) = s"http://${nexusHost}:8081/nexus/content/repositories/$releaseSnap"

credentials += Credentials(
  "Sonatype Nexus Repository Manager",
  "utility.allenai.org",
  "deployment",
  "answermyquery")

resolvers += "AllenAI Snapshots" at nexus("snapshots")

resolvers += "AllenAI Releases" at nexus("releases")

addSbtPlugin("org.allenai.plugins" % "sbt-shared-ui" % "0.2.5-SNAPSHOT")
