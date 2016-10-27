package org.allenai.plugins

import sbt.{ Artifact, Hash, IO, ModuleID }

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

  /** Given a set of files on disk, produce a consistent SHA1 hash of their contents. This hash will
    * change when the contents of the files change, or when the file name changes relative to the
    * given file.
    * @param filesToHash the files whose contents and names should be hashed
    * @param rootDir the root directory the file names should be resolved against before hashing.
    * Only path changes relative to this directory will cause the hash to change.
    */
  def hashFiles(filesToHash: Seq[File], rootDir: File): String = {
    // Resolve the filenames relative to the root directory.
    val rootDirPath = rootDir.toPath
    val relativizedNames = filesToHash.map(_.toPath).map(rootDirPath.relativize).map(_.toString)
    // Create a hash of the sorted names, joined by an empty string.
    val nameHash = Hash.toHex(Hash(relativizedNames.sorted.mkString))

    // Hash the contents of each file.
    val fileHashes = filesToHash.map(Hash.apply).map(Hash.toHex)

    // Finally, join the name hash with the content hashes, and hash the resulting string.
    Hash.toHex(Hash((nameHash +: fileHashes).sorted.mkString))
  }
}
