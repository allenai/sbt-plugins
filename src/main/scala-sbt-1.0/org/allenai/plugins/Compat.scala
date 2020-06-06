package org.allenai.plugins

import sbt._

object Compat {
  // This linter ignore is because of the `streams` calls being bad now.
  import sbt.dsl.LinterLevel.Ignore

  implicit class PathFinderImplicits(pf: PathFinder) {}

}
