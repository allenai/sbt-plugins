sbt-travis-publisher
===================

SBT plugin that limits publishing from TravisCI to releases only.

Currently, this plugin requires you to manually create a `.travis.yml` file
in the project's root with the following contents:

    language: scala
    scala:
    - 2.10.3
    jdk:
    - openjdk7
    after_success:
    - sbt publishMasterOnTravis
