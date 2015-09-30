val subProject1 = project.in(file("one"))
val subProject2 = project.in(file("two"))
val rootProject = project.in(file(".")).aggregate(subProject1, subProject2)
  .enablePlugins(ScaladocGenPlugin)

scaladocGenGitRemoteRepo := "git@github.com:allenai/sbt-plugins.git"

scaladocGenExtraScaladocMap := scaladocGenExtraScaladocMap.value +
  ("foobar" -> url("http://foobar.com"))
