package org.allenai.plugins.archetypes

import org.allenai.plugins.{ DeployPlugin, DockerBuildPlugin, NodeJsPlugin }
import org.allenai.plugins.NodeJsPlugin.autoImport.{ NodeKeys, Npm }

import com.typesafe.sbt.SbtNativePackager.Universal
import com.typesafe.sbt.packager.MappingsHelper
import com.typesafe.sbt.packager.universal.UniversalPlugin
import sbt.{ AutoPlugin, Def, Keys, Plugins, Setting, Test }
import spray.revolver.RevolverPlugin.Revolver

import java.io.File

/** Plugin that configures a webapp for building. This makes the `re-start`, `test`, and `clean`
  * tasks execute the appropriate node builds. It also configures the deploy plugin and docker
  * plugin appropriately.
  */
object WebappPlugin extends AutoPlugin {
  // TODO(jkinkead): Split the docker & deploy plugin stuff into separate archetype plugins that
  // depend on the base WebappPlugin.
  override def requires: Plugins = WebServicePlugin && NodeJsPlugin && DeployPlugin &&
    DockerBuildPlugin

  /** The node target directory, scoped appropriately. */
  lazy val nodeTargetDef: Def.Initialize[File] =
    Def.setting(NodeKeys.nodeProjectTarget.in(Npm).value)

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    // Run "npm watch" when we run a re-start.
    Revolver.reStart := Revolver.reStart.dependsOn(NodeKeys.nwatch.in(Npm)).evaluated,
    // Kill background watches on re-stop.
    Revolver.reStop := Revolver.reStop.dependsOn(NodeKeys.unwatch.in(Npm)).value,
    // Run client-side tests when tests are run.
    Keys.test.in(Test) := Keys.test.in(Test).dependsOn(Keys.test.in(Npm)).value,
    // Clean node files on clean.
    Keys.cleanFiles += nodeTargetDef.value,
    // Build the node project on stage (for deploys).
    UniversalPlugin.autoImport.stage :=
      UniversalPlugin.autoImport.stage.dependsOn(DeployPlugin.autoImport.deployNpmBuild).value,
    // Copy the built node project into our staging directory, too!
    Keys.mappings.in(Universal) ++= MappingsHelper.directory(nodeTargetDef.value),
    // Build the node project for docker builds.
    DockerBuildPlugin.autoImport.dockerMainStage := {
      DockerBuildPlugin.autoImport.dockerMainStage.dependsOn(
        DeployPlugin.autoImport.deployNpmBuild
      ).value
    },
    // Map the node target directory into the docker image.
    DockerBuildPlugin.autoImport.dockerCopyMappings ++=
      MappingsHelper.directory(nodeTargetDef.value),
    // Make `dockerRun` execute node watch, and `dockerStop` stop the watch.
    DockerBuildPlugin.autoImport.dockerRun :=
      DockerBuildPlugin.autoImport.dockerRun.dependsOn(NodeKeys.nwatch.in(Npm)).value,
    DockerBuildPlugin.autoImport.dockerStop :=
      DockerBuildPlugin.autoImport.dockerStop.dependsOn(NodeKeys.unwatch.in(Npm)).value,
    // When running with `dockerRun`, map the local filesystem's target directory in directly so
    // that rebuilds don't need to happen to see client-side changes.
    DockerBuildPlugin.autoImport.dockerRunFlags ++= {
      val volumeMapping = {
        val nodeDir = nodeTargetDef.value
        val imageTarget = DockerBuildPlugin.autoImport.dockerWorkdir.value + '/' + nodeDir.getName
        s"$nodeDir:$imageTarget:ro"
      }
      Seq("-v", volumeMapping)
    }
  )
}
