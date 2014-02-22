import sbt._
import Keys._

import com.typesafe.sbt.web.{ SbtWebPlugin => WebPlugin }
import com.typesafe.sbt.web.SbtWebPlugin.WebKeys
import com.typesafe.sbt.jse.{ SbtJsEnginePlugin => JsEnginePlugin, SbtJsTaskPlugin }
import com.typesafe.sbt.jshint.{ SbtJSHintPlugin => JSHintPlugin }
import com.typesafe.sbt.less.{ SbtLessPlugin => LessPlugin }

/** The SharedUiPlugin leverages Typesafe's sbt-web SBT plugin platform.
  *
  * This plugin depends on the following Typesafe plugins:
  *
  * 1. sbt-jshint-plugin [https://github.com/sbt/sbt-jshint-plugin] which provides
  *  Javascript 'linting' - essentially causing project compilation failure
  *  if we write some questionabel javascript.
  *
  * 2. sbt-less-plugin [https://github.com/sbt/sbt-less-plugin] which provides a
  *  Less compiler [http://lesscss.org/] to make CSS development easier and more maintainable.
  *
  */
object SharedUiPlugin extends Plugin {

  case class SharedProjectAssets(resourcesManaged: File, namespace: String)

  object SharedUiKeys {
    // public
    val lessFilter = SettingKey[Option[FileFilter]]("shared-less-filter")

    // internal
    val compileLocalAssets = TaskKey[Seq[File]](
      "compileLocalAssets",
      "Compile web assets in this project into the target/public directory")

    val compileSharedAssets = TaskKey[Seq[SharedProjectAssets]](
      "compileSharedAssets",
      "Compile web assets in shared projects")

    val copySharedAssetsToPublic = TaskKey[Seq[File]](
      "copySharedAssetsToPublic",
      "Copy compiled shared assets to this projects target/public directory")

    val copyCompiledAssetsToResources = TaskKey[Seq[File]](
      "copyCompiledAssetsToResources",
      "Copy all compiled assets in target/public to managed resources")

    // Default to avoid SBT error for unspecified setting
    compileSharedAssets := Nil
  }

  import SharedUiKeys._
  import LessPlugin.LessKeys

  /** Helper for consistent logging messages */
  private def log(msg: String) = s"[shared-ui] $msg"

  /** Compiles assets in all shared projects. */
  private def compileSharedAssetsTask(sharedProject: Project, namespace: String) =
    compileSharedAssets := {
      streams.value.log.info(log(s"Compiling shared assets in '${namespace}'"))
      val sharedState = (compile in WebKeys.Assets in sharedProject).value
      val resourcesMgd = (resourceManaged in WebKeys.Assets in sharedProject).value
      compileSharedAssets.value :+ SharedProjectAssets(resourcesMgd, namespace)
    }

  /** Once assets in all shared projects are compiled, copies them to target/public
    * in the provided namespaces.
    */
  private def copySharedAssetsToPublicTask = copySharedAssetsToPublic := {
    val sharedProjects = compileSharedAssets.value
    // require that all namespaces are unique:
    require(
      sharedProjects.groupBy(_.namespace).size == sharedProjects.size,
      log("Shared project namespace collision detected!"))

    sharedProjects.flatMap { sharedProjectAssets =>
      streams.value.log.info(log(s"Copying shared assets from '${sharedProjectAssets.namespace}' to target/public"))
      val newBase = (resourceManaged in WebKeys.Assets).value / sharedProjectAssets.namespace
      copyWebResources(Seq(sharedProjectAssets.resourcesManaged), newBase)
    }
  }

  /** Once all shared assets have been compiled and copied into target/public/[namespaces],
    * compiles the assets in the local project (to target/public)
    */
  private def compileLocalAssetsTask = compileLocalAssets := {
    streams.value.log.info(log("Compiling local assets"))
    val shared = copySharedAssetsToPublic.value
    val state = (compile in WebKeys.Assets).value
    (copyResources in WebKeys.Assets).value.map(_._2)
  }

  /** Copies the entire target/public directory into managed resources */
  private def copyCompiledAssetsToResourcesTask = copyCompiledAssetsToResources := {
    streams.value.log.info(log("Copying compiled assets to resources"))
    val allAssets = compileLocalAssets.value
    val newBase = (resourceManaged in Compile).value / "public"
    val public = (resourceManaged in WebKeys.Assets).value
    copyWebResources(public :: Nil, newBase)
  }

  /** bases Less CSS settings to be applied to all UI projects */
  private val lessBaseSettings = LessPlugin.lessSettings ++ Seq(
    SharedUiKeys.lessFilter := None,
    LessKeys.lessFilter := {
      SharedUiKeys.lessFilter.value match {
        case Some(filter) => filter
        case None         => (LessKeys.lessFilter in WebKeys.Assets).value
      }
    }
  )

  private val uiBaseSettings: Seq[Def.Setting[_]] = Seq(
    compileLocalAssetsTask,
    compileSharedAssets := Nil,
    copySharedAssetsToPublicTask,
    copyCompiledAssetsToResourcesTask,
    resourceGenerators in Compile <+= copyCompiledAssetsToResources,
    compile in Compile <<= (compile in Compile).dependsOn(compileLocalAssets)
  )

  /* ================> Public Interface ================ */

  /** Default settings for any project that creates web assets */
  val uiSettings: Seq[Def.Setting[_]] = WebPlugin.webSettings ++
    JsEnginePlugin.jsEngineSettings ++
    SbtJsTaskPlugin.jsEngineAndTaskSettings ++
    JSHintPlugin.jshintSettings ++
    lessBaseSettings ++
    uiBaseSettings

  /** Settings for dependent UI projects
    * Causes assets generated by sharedProject to be copied into
    * the dependent project's `/public/[namespace]` resource directory.
    *
    * @param sharedProject  the SBT project that generates shared web assets
    * @param namespace      the resource subdirectory to place the shared resources; defaults to "shared"
    */
  def uses(sharedProject: Project, namespace: String = "shared"): Seq[Def.Setting[_]] =
    Seq(compileSharedAssetsTask(sharedProject, namespace))

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
