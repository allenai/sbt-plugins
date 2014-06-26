(function() {
  'use strict';

  var AppController = function(scope) {
    scope.items = [
      "one",
      "two",
      "three",
      "four",
      "five"
    ];
  };

  module.exports = ['$scope', AppController];
})();
