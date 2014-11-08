sbt-style
==========

**Required by**: `CoreSettings`

**Wraps**:

- [scalariform](https://github.com/mdr/scalariform) for auto-formatting
- [scalastyle](http://www.scalastyle.org/) for style checking

**Note**: This plugin is auto-enabled as part of `CoreSettings`

Enabling this plugin will make a project's `compile` task depend on the format and style checker.

## Disabling warnings
To disable format warnings and auto-formatting for a block of code, bound it in these special comments:
```
// format: OFF

bad_code()

// format: ON
```
This is case-sensitive, but may have zero or more spaces after the `:`.


To disable style warnings for a block of code, bound it in these special comments:
```
// scalastyle:off

bad_code()

// scalastyle:on
```

Note that *no spaces* can happen after the `:` in the comment - this is different than with format warnings!

You can also disable style checks on a single line with a postfix comment:
```
bad_code() // scalastyle:ignore
```

## Installation

The plugin is an AutoPlugin which requires SBT version 0.13.5 or later.

To install, add the following to your `project/plugins.sbt`:

```scala
addSbtPlugin("org.allenai.plugins" % "allenai-sbt-style" % VERSION)
```
Substitute `VERSION` with the latest version for the plugin on [bintray](https://bintray.com/allenai/sbt-plugins).

The plugin should be auto-enabled for every project, so you don't have to explicitly enable it in `build.sbt`.

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

#### styleCheck

Check the source code's style. This will print out any warnings or errors, as
well as a cryptic success message:
```
> styleCheck
[warn] sbt-plugins/sbt-style/sbt-style-tester/src/main/scala/Main.scala:1: Line is more than 100 characters long
[warn] sbt-plugins/sbt-style/sbt-style-tester/src/main/scala/Main.scala:3:12: var initialized with integer literal; should be initialized from named constant or made val
[success] created: sbt.SettingKey$$anon$4@25d7a745
```

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
your source files. You can do this easily by creating the `pre-commit` file in your repository's `.git/hooks/` directory.

    #!/bin/sh

    trap 'exit 1' ERR
    /usr/local/bin/sbt -Dsbt.log.noformat=true warn compile test:compile formatCheckStrict test:formatCheckStrict

Don't forget to make your pre-commit script executable:

    $ chmod +x .git/hooks/pre-commit

Now `compile` and `formatCheckStrict` will run before you make a commit, for test and main sources. If you have
misformatted files, or files that don't compile, you will need to correct them before comitting.

### Code Style Settings for IntelliJ
This [jar file](https://github.com/allenai/sbt-plugins/blob/master/sbt-style/settings/AI2CodeStyleSettingsForIntelliJ.jar) contains 
settings for IntelliJ that conform as closely as possible to the formatting conventions of this 
plugin.  To use them, choose File->Import Settings and select the jar file.
