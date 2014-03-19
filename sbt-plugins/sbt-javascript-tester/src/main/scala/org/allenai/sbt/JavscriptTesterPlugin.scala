package org.allenai.sbt

import sbt._
import sbt.Keys._
import com.typesafe.sbt.web.SbtWebPlugin._
import com.typesafe.sbt.jse.SbtJsTaskPlugin

object JavascriptTesterPlugin extends AutoPlugin {

  def select = SbtJsTaskPlugin

  object JavascriptTesterKeys {
    val jstest = TaskKey[Seq[File]]("jstest", "Perform JavaScript testing.")
  }

  import WebKeys._
  import SbtJsTaskPlugin._
  import SbtJsTaskPlugin.JsTaskKeys._
  import JavascriptTesterKeys._

  val unscopedSettings = Seq(
    test in TestAssets <<= (test in TestAssets).dependsOn(jstest in TestAssets),
    test in Test <<= (test in Test).dependsOn(test in TestAssets)
  )

  override def projectSettings = unscopedSettings ++ inTask(jstest)(
    jsTaskSpecificUnscopedSettings ++ Seq(

      moduleName := "jstest",
      shellFile := "jstest-shell.js",
      fileFilter in Assets := "",
      fileFilter in TestAssets := (jsFilter in TestAssets).value && ("*Test.js" || "*Spec.js"),

      jsOptions := "{}",
      taskMessage in Assets := "JavaScript testing NOT",
      taskMessage in TestAssets := "JavaScript testing"
    )
  ) ++ addJsSourceFileTasks(jstest)
}
