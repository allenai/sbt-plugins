// Map `foo` in the project root to `bar` in the docker image.
dockerCopyMappings += (baseDirectory.value / "foo", "bar")

// Map src/main/sourcey to `sourcey` in the docker image.
dockerCopyMappings += (file("sourcey"), "sourcey")
