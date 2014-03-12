val nexusHost = "utility.allenai.org"

def nexus(releaseSnap: String) = s"http://${nexusHost}:8081/nexus/content/repositories/$releaseSnap"

credentials += Credentials(
  "Sonatype Nexus Repository Manager",
  "utility.allenai.org",
  "deployment",
  "answermyquery")

resolvers += "AllenAI Snapshots" at nexus("snapshots")

resolvers += "AllenAI Releases" at nexus("releases")

lazy val root = Project("plugins", file(".")).dependsOn(plugin)

lazy val plugin = ProjectRef(file("../../").getCanonicalFile.toURI, "sbtSharedUi")
