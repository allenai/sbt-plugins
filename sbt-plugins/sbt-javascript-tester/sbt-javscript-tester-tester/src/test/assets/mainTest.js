var assert = require("assert");
var MainModule = require("../../main/assets/main.js");

describe("Main", function() {
  describe("add", function() {
    it("should pass the test", function(done) {
      var m = new MainModule();
      assert.equal(4, m.add(1, 3));
      done();
    });
  });
});
