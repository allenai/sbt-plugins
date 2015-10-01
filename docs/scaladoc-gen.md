ScaladocGenPlugin
=================

**Boss**: Jesse

**Wraps**: [`sbt-unidoc`](https://github.com/sbt/sbt-unidoc), [`sbt-ghpages`](https://github.com/sbt/sbt-ghpages), and [`sbt-site`](https://github.com/sbt/sbt-site).

A plugin to generate Scaladoc for aggregate (root) projects in sbt.

See the sites plugin for more instructions on how to configure it, if you're interested in having more things on your github site than just scaladoc!

Usage
=====
Configuring:
```scala
// Enable the plugin on your project, which should aggregate all of your
// subprojects.
lazy val myRootProject = project.in(file(".")).aggregate(one, two)
  .settings(
    // This setting is required in order for the plugin to load.
    scaladocGenGitRemoteRepo := "git@github.com:allenai/myprojectname.git",
    // This is not strictly required, but embeds this URL into your artifact's
    // pom.xml so that sbt can automatically generate links to it from other
    // projects. You should set this for any project that will be released as a
    // library.
    apiURL := Some(url("https://allenai.github.io/myprojectname/latest/api/"))
  )
  .enablePlugins(ScaladocGenPlugin)
```

Commands in sbt:
  * **unidoc**: This will generate all scaladoc for the root project.
  * **preview-site**: Generate the full site and view it in a browser.
  * **ghpages-push-site**: Generate the full site and push it to github pages. Note that you need to have a `ghpages` branch configured on the remote repository or this will fail. See [the ghpages docs](https://github.com/sbt/sbt-ghpages#creating-ghpages-branch) for a concise summary of how to do this.

Setting keys:
  * `scaladocGenGitRemoteRepo` - This needs to be set to the remote repository to push github pages to.
  * `scaladocGenJavadocUrl` - This is set to the root Javadoc URL to link to. Defaults to Java 8 on the Oracle site.
  * `scaladocGenExtraJavadocMap` - Any extra Java libraries you'd like to have linked automatically from Scaladoc. Should normally be set retaining the old values: `scaladocGenExtraJavadocMap := scaladocGenExtraJavadocMap.value ++ Map("new" -> url("values"))`.
  * `scaladocGenExtraScaladocMap` - Any extra Scala libraries you'd like to have linked automatically from Scaladoc. Should normally be set retaining the old values: `scaladocGenExtraScaladocMap := scaladocGenExtraScaladocMap.value ++ Map("new" -> url("values"))`.
  * `fixNullCurrentBranch` - A special setting that fixes a bug in the SbtGit plugin used by `sbt-ghpages`. This should be included in sub-projects in any build that's rooted in a subdirectory of a git repository (any build that doesn't have a `.git` directory in the same directory as the root `build.sbt` file).
