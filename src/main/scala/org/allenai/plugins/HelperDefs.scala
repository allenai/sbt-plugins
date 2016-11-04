package org.allenai.plugins

import sbt.{
  task,
  Artifact,
  AttributeKey,
  BuildDependencies,
  Compile,
  Def,
  Extracted,
  Keys,
  ModuleID,
  Project,
  ProjectRef,
  Runtime,
  State,
  Task
}

import java.io.File

import scala.sys.process.Process

/** Helper tasks for building plugins. */
object HelperDefs {
  /** Task initializer to look up non-local runtime dependency artifacts for the current project.
    * This contains all direct and transitive dependencies pulled from remote sources (i.e. maven
    * repositories).
    *
    * This task result is the pairing of dependency jars with their target filenames.
    */
  lazy val remoteDependencies: Def.Initialize[Task[Seq[(File, String)]]] = Def.task {
    // The runtime classpath includes all jars needed to run, as well as the target directories for
    // local dependencies (and the current project).
    val allDependencies = Keys.fullClasspath.in(Runtime).value
    // Filter out dependencies that don't have an artifact to include and those that aren't files
    // (the directory targets).
    val jarDependencies = allDependencies.filter { dependency =>
      dependency.get(Keys.artifact.key).nonEmpty && dependency.data.isFile
    }

    // Map to the file / rename pairings.
    jarDependencies.map { dependency =>
      val file = dependency.data
      val jarName = {
        // Try to generate a good jar name from the artifact data; else, use the filename.
        val moduleIdOption = dependency.metadata.get(AttributeKey[ModuleID]("module-id"))
        val artifactOption = dependency.metadata.get(AttributeKey[Artifact]("artifact"))
        val generatedNameOption = moduleIdOption.zip(artifactOption).headOption.map {
          case (moduleId, artifact) => Utilities.jarName(moduleId, artifact)
        }
        generatedNameOption.getOrElse(file.getName)
      }
      (file, jarName)
    }
  }

  /** Task initializer to build local runtime dependency artifacts for the current project. This
    * will be the packaged binary jar for the current project, plus the packaged binary jars for any
    * local subproject dependencies.
    *
    * This task result is the pairing of dependency jars with their target filenames.
    */
  lazy val localDependencies: Def.Initialize[Task[Seq[(File, String)]]] = Def.taskDyn {
    // The current project & its dependencies.
    val allProjects: Seq[ProjectRef] = {
      val thisProject: ProjectRef = Keys.thisProjectRef.value
      // All projects the current project depends on.
      val dependencyProjects: Seq[ProjectRef] = {
        val buildDependenciesValue: BuildDependencies = Keys.buildDependencies.value
        buildDependenciesValue.classpathTransitive.get(thisProject).getOrElse(Seq.empty)
      }
      thisProject +: dependencyProjects
    }

    Keys.state.apply { stateTask: Task[State] =>
      // Create tasks to look up all artifacts for each project.
      val allArtifactTasks: Seq[Task[(File, String)]] = allProjects.map { projectRef =>
        stateTask.flatMap { stateValue =>
          val extracted: Extracted = Project.extract(stateValue)
          val jarName: String = {
            val moduleId: ModuleID = extracted.get(Keys.projectID.in(projectRef))
            val artifact: Artifact = extracted.get(Keys.artifact.in(projectRef))
            Utilities.jarName(moduleId, artifact)
          }
          val binaryTask: Task[File] =
            extracted.get(Keys.packageBin.in(Compile).in(projectRef))
          binaryTask.map { binary =>
            (binary, jarName)
          }
        }
      }
      // Collapse the tasks into a single task.
      allArtifactTasks.foldLeft(task[Seq[(File, String)]](Seq.empty)) { (aggTask, currTask) =>
        currTask.flatMap { currPair: (File, String) =>
          aggTask.map { aggPairs: Seq[(File, String)] => aggPairs :+ currPair }
        }
      }
    }
  }

  /** Task initializer to check if a git repository is present. Returns true if running in a git
    * repository, false otherwise.
    */
  lazy val gitRepoPresentDef: Def.Initialize[Task[Boolean]] = Def.task {
    Process(Seq("git", "status")).!(Utilities.NIL_PROCESS_LOGGER) != 0
  }

  /** Task initializer to check if a git repository is present and clean. Returns an error message
    * if not running in a git repository, if there are uncommitted changes, or if there are
    * untracked files.
    */
  lazy val gitRepoCleanDef: Def.Initialize[Task[Option[String]]] = Def.task {
    if (!gitRepoPresentDef.value) {
      Some("Git repository is missing")
    } else if (Process(Seq("git", "diff", "--shortstat")).!! != "") {
      Some("Git repository is dirty")
    } else if (Process(Seq("git", "clean", "-n")).!! != "") {
      Some("Git repository has untracked files")
    } else {
      None
    }
  }
}
