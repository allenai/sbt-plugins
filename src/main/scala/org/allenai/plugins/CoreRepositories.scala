package org.allenai.plugins

import bintray.BintrayKeys._
import sbt._
import sbt.Keys._

object CoreRepositories {

  /** Common licenses */
  object Licenses {
    val apache2 = ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))
  }

  /** Common resolvers */
  object Resolvers {
    val ai2PrivateReleases = Resolver.bintrayRepo("allenai", "private")
    val ai2PublicReleases = Resolver.bintrayRepo("allenai", "maven")

    // needed for spray-json:
    val spray = "spray".at("https://repo.spray.io/")

    val typesafeReleases = "Typesafe Releases".at("https://repo.typesafe.com/typesafe/releases/")

    /** Default set of resolvers that will be added via CoreSettings */
    val defaults = Seq(
      spray,
      Resolver.jcenterRepo,
      typesafeReleases
    )
  }

  /** Provides publishTo setting for specific repositories */
  object PublishTo {

    import Resolvers._

    /** Sets bintray keys to publish to the private bintray repository. This expects at least one
      * license to be set, e.g. `licenses += Licenses.apache2`.
      */
    val ai2BintrayPrivate = Seq(
      bintrayPackage := s"${organization.value}:${name.value}_${scalaBinaryVersion.value}",
      bintrayRepository := "private"
    )

    /** Sets bintray keys to publish to the public bintray repository. This expects at least one
      * license to be set, e.g. `licenses += Licenses.apache2`.
      */
    val ai2BintrayPublic = Seq(
      bintrayPackage := s"${organization.value}:${name.value}_${scalaBinaryVersion.value}",
      bintrayRepository := "maven"
    )
  }
}
