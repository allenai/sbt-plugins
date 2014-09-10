var assert = require('assert');

var appController = require('./app-controller');

var Controller = appController[appController.length - 1];

var newController = function(scope) {
  return new Controller(scope);
};

describe('app-controller', function() {
  describe('items', function() {
    it('should be initialized with zero elements', function() {
      var mockScope = {};
      var ctrl = newController(mockScope);
      assert.equal(0, mockScope.items.length);
    });
  });

  describe('addItem', function() {
    it('should add an item to the end', function() {
      var mockScope = {};
      var ctrl = newController(mockScope);
      mockScope.addItem("one");
      mockScope.addItem("two");
      assert.equal(2, mockScope.items.length);
    });
  });

  describe('removeItem', function() {
    it('should remove an item', function() {
      var mockScope = {};
      var ctrl = newController(mockScope);
      mockScope.addItem("one");
      mockScope.addItem("two");
      mockScope.addItem("three");
      mockScope.removeItem("two");
      assert.equal(2, mockScope.items.length);
      assert.equal(-1, mockScope.items.indexOf("two"));
    });
  });
});
