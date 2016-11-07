# [Docker Build Plugin](src/main/scala/org/allenai/plugins/DockerBuildPlugin.scala)

This plugin provides integration with [Docker Engine](https://docs.docker.com/engine/installation/). It helps you generate Dockerfiles, build images, run images, and push images to a Docker registry.

The intent is to provide very easy interaction with Docker, without needing to understand low-level details around how Docker works. However, default behavior is easy to override, as documented below.

This plugin requires you have access to the `docker` command in your shell.  See the [Docker install page](https://www.docker.com/products/overview#/install_the_platform) to install.

## Overview - Basic Workflow

If you don't have a [Dockerfile](https://docs.docker.com/engine/reference/builder/):

0. Generate a  for your project. This can be done automatically with the [generateDockerfile](#generateDockerfile) task.

Once you have a Dockerfile:

1. Run [dockerBuild](#dockerBuild) to build your image.
2. Run [dockerRun](#dockerRun) to run your image in a container locally.
3. Run [dockerPush](#dockerPush) to push your image to a registry.

## Command Details

### generateDockerfile

TODO: Document.
