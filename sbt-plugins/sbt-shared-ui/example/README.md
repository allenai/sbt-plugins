sbt-shared-ui example
===================

To run the example, start `sbt` and perform the following tasks:

    > project ui1 (or ui3)
    > ~reStart

Navigate your browser to `http://localhost:8090`

Your browser should display a beautiful UI. You can change the font color and styling of the text in:

`shared/src/main/assets/shared/less/shared.less`

Make a change and you should notice your Spray server compile the Less CSS and then
restart. Now you can refresh your browser and see the changes were picked up.

Changing javascript sources will trigger linting via JsHint. Make a bad editing choice and you'll
see compilation will fail with informative messages in the console.

## Example servers ##

Examples are provided to show (and test) various dependency configurations.

### ui1 -> shared ###

### ui2 -> shared ###

### ui3 -> ui1 -> shared ###

For a more interesting example, check out the `ui3` project which has a transitive dependency on
`shared` through `ui1`:

To run `ui3`, start `sbt` and perform the following tasks:

    > project ui3
    > ~reStart

Navigate your browser to `http://localhost:8093`

For fun, you can load the `ui1` application here: `http://localhost:8093/ui1`

And of course, change any sources in `shared`, `ui1`, or `ui3` and triggered exection will
pick them up in the running `ui3` project.
