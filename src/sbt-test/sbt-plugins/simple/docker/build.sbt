// Map `foo` in the project root to `bar` in the docker image.
dockerCopyMappings += (file("foo"), "bar")

// Map src/main/sourcey to `sourcey` in the docker image.
dockerCopyMappings += (sourceDirectory.value / "main" / "sourcey", "sourcey")

lazy val appendToDockerfile = taskKey[Unit]("append to dockerfile")
appendToDockerfile := {
  val dockerfile = dockerfileLocation.value
  IO.append(dockerfile, "# TESTING\n")
}
