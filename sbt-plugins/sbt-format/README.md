sbt-format
==========

A plugin for formatting Scala source code.

## Installation

The plugin is an AutoPlugin which requires SBT version 0.13.5 or later.

To install, add the following to your `project/plugins.sbt`:

```scala
addSbtPlugin("org.allenai.plugins" % "allenai-sbt-format" % VERSION)
```

Then, enable the plugin per project in your `build.sbt`:

```scala
val myProject = project.in(file(".")).enablePlugins(FormatPlugin)
```

### Tasks

#### format

Format the source code.  This is the same as `scalariformFormat`.

```
> format
[info] Formatting 4 Scala sources {file:/home/michael/hack/github/allenai/common/}testkit(compile) ...
[info] Formatting 8 Scala sources {file:/home/michael/hack/github/allenai/common/}common(compile) ...
[info] Reformatted 5 Scala sources {file:/home/michael/hack/github/allenai/common/}common(compile).
```

Tip: if you have a clean git index, you can revert scalariform changes with
`git reset HEAD --hard`.

#### formatCheck

Check that the source code is formatted.  Misformatted source file will be
announced with a warning.  The response of this task is a sequence of the
misformatted files (for chaining of sbt tasks).

```
> formatCheck
[warn] misformatted: BufferedIteratorUtil.scala
[warn] misformatted: Enum.scala
[warn] misformatted: Interval.scala
[warn] misformatted: Logging.scala
[warn] misformatted: Timing.scala
```

#### formatCheckStrict

Check that the source code is formatted.  Misformatted source file will be
announced with a warning.  The exit code will be non-zero.

### Pre-commit Hook

You might want a pre-commit hook that makes sure you've correctly formatted
your source files.  You can do this easily.

    $ echo '#!/bin/bash

    sbt formatCheckStrict' > .git/hooks/pre-commit
    $ chmod +x .git/hooks/pre-commit

Now `formatCheckStrict` will run before you make a commit.  If you have
misformatted files, you will need to correct them before comitting.
