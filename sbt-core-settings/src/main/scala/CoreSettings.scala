package org.allenai.sbt.core

import sbt._
import sbt.Keys._

/** Declares common settings to encourage
  * consistency accross projects
  */
trait CoreSettings {
  val Dependencies = CoreDependencies

  object publishToRepos {
    val sonatype=
      publishTo := {
        if (isSnapshot.value)
          Some("snapshots" at "https://oss.sonatype.org/content/repositories/snapshots")
        else
          Some("releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")
      }

    val ai2 = {
      val ai2NexusHost = "utility.allenai.org"
      val ai2NexusUrl = s"http://${ai2NexusHost}:8081/nexus/content/repositories/"
      publishTo := {
        if (isSnapshot.value)
          Some("snapshots" at ai2NexusUrl + "snapshots")
        else
          Some("releases" at ai2NexusUrl + "releases")
      }
    }
  }

  object resolverRepos {
    val defaults = Seq(
      // needed for spray-json:
      spray,
      typesafe.releases
    )

    val spray = "spray" at "http://repo.spray.io/"

    object typesafe {
      val releases = "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"
    }

    object allenAi {
      val snapshots =
        "AllenAI Snapshots" at "http://utility.allenai.org:8081/nexus/content/repositories/snapshots"

      val releases =
        "AllenAI Releases" at "http://utility.allenai.org:8081/nexus/content/repositories/releases"
    }
  }
}

object CoreSettings extends CoreSettings
