/*
 * Test Javascript files using Jasmine
 *
 * Arguments:
 * 0 - name given to the command invoking the script (unused)
 * 1 - filepath of this script (unused)
 * 2 - array of file paths to the test files
 *
 */
(function() {
  "use strict";

  var args = process.argv;
  var console = require("console");
  var mocha = require("mocha");

  var SOURCE_FILE_MAPPINGS_ARG = 2;

  options = {
    'reporter': mocha.reporters.Spec
  };

  var sourceFileMappings = JSON.parse(args[SOURCE_FILE_MAPPINGS_ARG]);
  var sourceFilesToProcess = sourceFileMappings.length;

  var results = [];
  var problems = [];

  sourceFileMappings.forEach(function (sourceFilePath) {

    var sourcePath = sourceFilePath[0];

    var tester = new mocha(options);
    tester.addFile(sourcePath);

    var runner = tester.run(function() {
      if (--sourceFilesToProcess == 0) {
        console.log("\u0010" + JSON.stringify({results: results, problems: problems}));
      }
    });

    runner.on('test end', function(test) {
      if (test.state == "passed" || test.state == "pending") {
        // all good
      } else {
        problems.push({
          message: test.title + " [" + test.state + "]",
          severity: 1,
          lineContent: "",
          source: sourcePath
        });
      }
    });
  });
}());
