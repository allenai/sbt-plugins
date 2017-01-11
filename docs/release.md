# allenai-sbt-release

AutoPlugin that provides a custom release process.

**Required by**: `LibraryPlugin`

**Wraps**: [sbt-release](https://github.com/sbt/sbt-release)

The release plugin's default configuration is for releases that are made by the
continuous build system to BinTray.  Specifically, the plugin configures the
`sbt release` command to make commits and tags only--it does not actually
publish.  It's expected that the CI system is configured to recognize the tag
and kick off a publish. See the section below for instructions.

## Releasing with the plugin

After the plugin is installed, release using the following checklist:

1.  Make sure you are on a branch of the main repository other than `master`.
**You cannot use a branch of your fork for releases.**  If you do, your tag
will not make it to the main repository.
2.  Set the upstream repository: `git push --set-upstream $upstream_name $branch_name`
  * Note that `$upstream_name` is the name of the git remote pointing to the main repository, which you can see using `git remote -v`.
3.  Kick off the release with `sbt release`.
4.  Create a pull request and make sure Semaphore builds it OK.
5.  When the pull request is merged, Semaphore will kick off a build and ultimately publish the artifact.
6.  You can verify that it was published by [looking on BinTray.com](https://bintray.com).

## Installation

### Configure local build

Enable the plugin for your **root** project in `build.sbt`:

```scala
val myProject = project.in(file(".")).enablePlugins(Ai2ReleasePlugin)
```

**NOTE FOR OLDER PROJECTS**: If you're migrating an older project (one using a date-based versioning
scheme), you'll also need to update your `version.sbt` file to contain a semantic version.

### Multi-project builds
If your project consists of subprojects, you must create an aggregate project that:
- has the `Ai2ReleasePlugin` enabled
- has stubbed-out publishing settings
- aggregates all subprojects that will be released

Here is an example multi-build project `build.sbt`:

```scala
lazy val releaseProject = project.in(file(".")).settings(
    publish := { },
    publishTo := Some("bogus" at "http://nowhere.com"),
    publishLocal := { })
   .enablePlugins(Ai2ReleasePlugin)
   .aggregate(api)

// The project you wish to release
lazy val api = project.in(file("api")).enablePlugins(LibraryPlugin)

// Another project you wish to release
lazy val service = project.in(file("util")).enablePlugins(LibraryPlugin)
```

You also need to configure your publication destination. For internal projects, you probably want
to use Nexus. Note that you also have to disable Bintray:
```scala
import org.allenai.plugins.CoreRepositories.PublishTo

lazy val internalApi = project.in(file("internal-api"))
  .settings(PublishTo.ai2Private)
  .settings(disableBintray(): _*)
  .enablePlugins(LibraryPlugin)
```
For open-sourced (public) projects, you should use Bintray. This is the default, so you don't have to configure anything extra to make it work:
```scala
lazy val externalApi = project.in(file("external-api")).enablePlugins(LibraryPlugin)
```

## Configure your continuous integration system

### Semaphore

You need to do two things: Configure your credentials file, and configure the release script.

#### Credentials

*Important*: Make sure you check the "Encrypt file" option before you save any credentials files!

Nexus:
Go to "Project Settings" > "Configuration Files", and add a file in `~/.sbt/0.13/credentials.sbt`.
This should contain our nexus credentials:
```scala
credentials += Credentials(
  "Sonatype Nexus Repository Manager",
  "utility.allenai.org",
  "deploy",
  $AI2_NEXUS_PASSWORD
)
```
Where `$AI2_NEXUS_PASSWORD` is the Nexus password. This should be in your own `~/.sbt/0.13`
directory, if you followed the Getting Started guide.

Bintray:

Go to "Project Settings" > "Configuration Files", and add a file in `~/.bintray/.credentials`. This
should contain our ai2-dev bintray credentials:
```
realm = Bintray API Realm
host = api.bintray.com
user = ai2-dev
password = $AI2_DEV_KEY
```
Where `$AI2_DEV_KEY` is the actual dev key for the ai2-dev user.


#### Build script

In addition to the build settings above, you need to have a script that does the publication. Copy the following
to a location in your project (e.g. `bin/publish.sh`), and add a build step that runs the script
(e.g. `bash bin/publish.sh`).

```shell
#!/bin/bash

set -e

# Publish to BinTray if the HEAD commit is tagged with a version number.
if [ "$PULL_REQUEST_NUMBER" ]; then
  echo "Semaphore is building a pull request, not publishing."
  echo "PULL_REQUEST_NUMBER is equal to $PULL_REQUEST_NUMBER"
  exit 0
fi

if [ "$BRANCH_NAME" != "master" ]; then
  echo "Semaphore is building on branch $BRANCH_NAME, not publishing."
  echo "BRANCH_NAME is equal to $BRANCH_NAME"
  exit 0
fi

numParents=`git log --pretty=%P -n 1 | wc -w | xargs`
if [ $numParents -ne 2 ]; then
  echo "$numParents parent commits of HEAD when exactly 2 expected, not publishing."
  exit 0
fi

# One build is run for the merge to master, so we need to list all tags from the merged commits.
firstMergedCommit=`git rev-list HEAD^2 --not HEAD^1 | tail -n 1`
echo "First merged commit: $firstMergedCommit"

tags=$(git tag --contains $firstMergedCommit)

if [ `echo "$tags" | wc -l` -eq 0 ]; then
  echo "No tags found in merged commits, not publishing."
  exit 0
fi

if [ `echo "$tags" | wc -l` -gt 1 ]; then
  echo "Multiple tags found in merged commits, not publishing."
  echo "$tags"
  exit 0
fi

tag=$tags

echo "Merged commits contain tag: $tag"

if [[ $tag =~ ^v[0-9]+\..* ]]; then
  echo "Going to release from tag $tag"
  version=$(echo $tag | sed -e s/^v//)

  git checkout $tag
  sbt publish
  echo "Successfully published artifact."

  exit 0
fi
```

### Shippable

See [allenai/common](https://github.com/allenai/common), especially `shippable.yml` and the `admin` folder.
