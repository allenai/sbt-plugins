import sbt._
import Keys._

import com.typesafe.sbt.web._
import com.typesafe.sbt.web.SbtWebPlugin.WebKeys
import com.typesafe.sbt.jse.{ SbtJsEnginePlugin, SbtJsTaskPlugin }
import com.typesafe.sbt.jshint.SbtJSHintPlugin
import com.typesafe.sbt.less.SbtLessPlugin

/** The SharedUiPlugin leverages Typesafe's sbt-web SBT plugin platform.
  *
  * This plugin depends on the following Typesafe plugins:
  *
  * 1. sbt-jshint-plugin [https://github.com/sbt/sbt-jshint-plugin] which provides
  * Javascript 'linting' - essentially causing project compilation failure
  * if we write some questionabel javascript.
  *
  * 2. sbt-less-plugin [https://github.com/sbt/sbt-less-plugin] which provides a
  * Less compiler [http://lesscss.org/] to make CSS development easier and more maintainable.
  *
  */
object SharedUiPlugin extends Plugin {

  case class SharedProjectAssets(webTarget: File)

  object SharedUiKeys {
    // public
    val lessFilter = SettingKey[Option[FileFilter]]("shared-less-filter")

    // internal
    val compileSharedAssets = TaskKey[Seq[SharedProjectAssets]](
      "compileSharedAssets",
      "Compile web assets in shared projects")

    val copySharedAssetsToWebTarget = TaskKey[Seq[File]](
      "copySharedAssetsToWebTarget",
      "Copy compiled shared assets to this projects target/web directory")

    val copyCompiledAssetsToResources = TaskKey[Seq[File]](
      "copyCompiledAssetsToResources",
      "Copy all compiled assets in target/web to managed resources")

    // Default to avoid SBT error for unspecified setting
    compileSharedAssets := Nil
  }

  import SharedUiKeys._
  import SbtLessPlugin.LessKeys

  // Provides the fileFilter key used by the lessFilter setting.
  import SbtJsTaskPlugin.JsTaskKeys._

  /** Helper for consistent logging messages */
  private def log(projName: String, msg: String) = s"[shared-ui][$projName] $msg"

  /** Compiles assets in all shared projects. */
  private def compileSharedAssetsTask(sharedProject: Project) =
    compileSharedAssets := {
      streams.value.log.info(log(name.value, s"Compiling shared assets in '${sharedProject.id}'"))
      val sharedState = (WebKeys.assets in WebKeys.Assets in sharedProject).value
      val webTarget = (WebKeys.webTarget in WebKeys.Assets in sharedProject).value
      compileSharedAssets.value :+ SharedProjectAssets(webTarget)
    }

  /** Once assets in all shared projects are compiled, copies them to target/web */
  private def copySharedAssetsToWebTargetTask = copySharedAssetsToWebTarget := {
    streams.value.log.info(log(name.value, "Copying shared assets to target/web"))
    val sharedProjects = compileSharedAssets.value
    sharedProjects.flatMap { sharedProjectAssets =>
      streams.value.log.info(log(name.value, s"Copying shared assets from '${sharedProjectAssets.webTarget.toString}' to target/web"))
      val newBase = (WebKeys.webTarget in WebKeys.Assets).value
      copyWebResources(Seq(sharedProjectAssets.webTarget), newBase)
    }
  }

  /** Copies the entire target/web directory into managed resources */
  private def copyCompiledAssetsToResourcesTask = copyCompiledAssetsToResources := {
    streams.value.log.info(log(name.value, "Copying compiled assets to managed resources"))
    val allAssets = filesOnly((WebKeys.assets in WebKeys.Assets).value)
    val newBase = (resourceManaged in Compile).value / "web"
    val webTarget = (WebKeys.webTarget in WebKeys.Assets).value
    copyWebResources(webTarget :: Nil, newBase)
  }

  /** bases Less CSS settings to be applied to all UI projects */
  private val lessBaseSettings = Seq(
    SharedUiKeys.lessFilter := None
  ) ++ inTask(LessKeys.less)(
      Seq(
        fileFilter := SharedUiKeys.lessFilter.value.getOrElse(fileFilter.value)
      ))

  private val uiBaseSettings: Seq[Def.Setting[_]] = Seq(
    compileSharedAssets := Nil,
    copySharedAssetsToWebTargetTask,
    copyCompiledAssetsToResourcesTask,
    resourceGenerators in Compile <+= copyCompiledAssetsToResources,
    WebKeys.assets in WebKeys.Assets <<= (WebKeys.assets in WebKeys.Assets).dependsOn(copySharedAssetsToWebTarget),
    compile in Compile <<= (compile in Compile).dependsOn(WebKeys.assets in WebKeys.Assets)
  )

  /* ================> Public Interface ================ */

  /** Default settings for any project that creates web assets */
  val uiSettings: Seq[Def.Setting[_]] = lessBaseSettings ++ uiBaseSettings

  /** Settings for dependent UI projects
    * Causes assets generated by sharedProject to be copied into
    * the dependent project's `target/web` resource directory.
    *
    * @param sharedProject  the SBT project that generates shared web assets
    */
  def uses(sharedProject: Project): Seq[Def.Setting[_]] =
    Seq(compileSharedAssetsTask(sharedProject))

  /** Helper that walks the directory tree and returs list of files only */
  private def filesOnly(source: File): Seq[File] =
    if (!source.isDirectory) source :: Nil
    else Option(source.listFiles) match {
      case None        => Nil
      case Some(files) => files flatMap filesOnly
    }

  /** Helper for copying web resource files */
  private def copyWebResources(baseDirectories: Seq[File], newBase: File): Seq[File] = {
    baseDirectories filterNot { _.exists } foreach { _.mkdirs() }
    if (!newBase.exists)
      newBase.mkdirs()

    val sourceFiles = baseDirectories flatMap filesOnly
    val mappings = sourceFiles pair rebase(baseDirectories, newBase)
    IO.copy(mappings, true).toSeq
  }

}
