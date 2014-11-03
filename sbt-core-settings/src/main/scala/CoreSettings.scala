package org.allenai.sbt.core

import sbt._
import sbt.Keys._

/** Declares common settings to encourage
  * consistency accross projects
  */
trait CoreSettings {
  val Dependencies = CoreDependencies

  object publishToRepos {
    val sonatype =
      publishTo := {
        if (isSnapshot.value)
          Some("snapshots" at "https://oss.sonatype.org/content/repositories/snapshots")
        else
          Some("releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")
      }

    object ai2 {
      val ai2NexusUrl = "http://utility.allenai.org:8081/nexus"
      val ai2RepoUrl = s"${ai2NexusHost}/content/repositories/"
      val public = {
        publishTo := {
          if (isSnapshot.value)
            Some("snapshots" at ai2RepoUrl + "snapshots")
          else
            Some("releases" at ai2RepoUrl + "releases")
        }
      }

      val public = {
        publishTo := {
          if (isSnapshot.value)
            Some("snapshots" at ai2RepoUrl + "public-snapshots")
          else
            Some("releases" at ai2RepoUrl + "public-releases")
        }
      }
    }
  }

  object resolverRepos {
    // needed for spray-json:
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

    val defaults = Seq(
      spray,
      typesafe.releases)
  }
}

object CoreSettings extends CoreSettings
