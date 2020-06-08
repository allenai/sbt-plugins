package org.allenai.plugins

import sbt._
import Append._

// Contains implicit methods to make 0.13 compatible with 1.0.
object Compat {

  // This is a mapping of the new syntax to the old. See:
  // https://github.com/sbt/sbt/blob/2952a2b9b672c5402b824ad2d2076243eb643598/util/io/src/main/scala/sbt/Path.scala#L107
  implicit class PathFinderImplicits(pf: PathFinder) {
    def allPaths: PathFinder = pf.***
  }

  // Modification of the existing implicit defs. See:
  // https://github.com/sbt/sbt/blob/ee272d780ec65466c927d85d27021e216c93950e/main-settings/src/main/scala/sbt/Append.scala#L51
  implicit def appendSeqToSet[T, V <: T]: Sequence[Set[T], Seq[V], V] =
    new Sequence[Set[T], Seq[V], V] {
      def appendValues(a: Set[T], b: Seq[V]): Set[T] = a ++ b.toSet
      def appendValue(a: Set[T], b: V): Set[T] = a + b
    }
}
