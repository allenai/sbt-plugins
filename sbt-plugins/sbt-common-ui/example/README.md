sbt-common-ui example
===================

To run the example, start `sbt` and perform the following tasks:

    > project ui1
    > ~re-start

Navigate your browser to `http://localhost:8090/assets/common/js/main.js`

Your browser should display a javascript file contents. This file is sourced in:

`common/src/main/assets/js/main.js`

Make a change to the javascript file and save it. You should notice your Spray server
is automatically restarted. Now you can refresh your browser and see the changes were
picked up.
  
