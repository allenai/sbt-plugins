var assert = require("assert");

describe("Second", function() {
  describe("index", function() {
    it("should pass the test", function(done) {
      assert.equal(-1, [1, 2, 3].indexOf(4));
      done();
    });
  });
});
