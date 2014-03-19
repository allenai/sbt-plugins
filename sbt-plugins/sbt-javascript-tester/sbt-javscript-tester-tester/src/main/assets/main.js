if (typeof define !== 'function') {
  var define = require('amdefine')(module);
}

define([], function() {
  "use strict";

  var MainModule = function() {
    this.add = function(a, b) {
      return a + b;
    };
  };

  return MainModule;
});
