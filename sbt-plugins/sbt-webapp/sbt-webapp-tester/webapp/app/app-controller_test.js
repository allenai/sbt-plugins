var AppController = require('./app-controller')[1];
var assert = require('assert');

describe('AppController', function() {
  describe('items', function() {
    it('should initialize with 5 items', function() {
      var scope = {};
      var ctrl = new AppController(scope);
      assert.equal(scope.items.length, 5);
    });
  });
});
