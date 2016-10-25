package org.allenai.plugins

import sbt.{ Artifact, IO, ModuleID }

import java.io.File

/** Helper methods and values for building plugins. */
object Utilities {
  /** Construct a unique jar name from the given module and artifact data. This will be of the form:
    * {{{
    * "${module.organization}.${module.name}-${artifact.name}-" +
    *   "${module.revision}-${artifact.classifier}.jar"
    * }}}
    * Classifier will be dropped if it's unset. The artifact name will be dropped if it exactly
    * matches the module name, and will have the module name stripped out regardless.
    */
  def jarName(module: ModuleID, artifact: Artifact): String = {
    val jarName = new StringBuilder(module.organization).append('.').append(module.name).append('-')

    // Elide the artifact name if it exactly matches the module name.
    if (module.name != artifact.name && artifact.name.nonEmpty) {
      // Replace any occurance of the module name, to remove redundancy.
      val strippedArtifactName = artifact.name.replace(module.name, "")
      jarName.append(strippedArtifactName).append('-')
    }

    jarName.append(module.revision)

    if (artifact.classifier.nonEmpty && artifact.classifier.get.nonEmpty) {
      jarName.append('-').append(artifact.classifier.get)
    }

    jarName.append(".jar").toString
  }

  /** Copies a class resource with the given name to a given location.
    * @param clazz the class to load the resource from
    * @param resourceName the name of the resource to copy
    * @param destination the destination file for the resource
    */
  def copyResourceToFile(clazz: Class[_], resourceName: String, destination: File): Unit = {
    val contents = {
      val is = clazz.getResourceAsStream(resourceName)
      try {
        IO.readBytes(is)
      } finally {
        is.close()
      }
    }
    IO.write(destination, contents)
  }
}
