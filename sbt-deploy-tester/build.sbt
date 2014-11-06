val test = project.in(file(".")).enablePlugins(DeployPlugin)

val printEnv = TaskKey[Unit]("printEnv", "Print the deploy environment")

printEnv := {
  val s = streams.value
  val env = deployEnvironment.value
  s.log.info(s"Environment: $env")
}

compile in Compile <<= (compile in Compile).dependsOn(printEnv)
