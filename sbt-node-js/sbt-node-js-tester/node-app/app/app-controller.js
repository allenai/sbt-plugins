(function() {
  'use strict';

  var AppController = function(scope, http, window) {

    var API_ROOT = window.appConfig.apiHost;

    scope.items = [];

    // Model mirroring Submit model defined in ApiRoute.scala
    // Used for data binding and will be submitted as JSON when
    // user clicke ths 'Submit' button.
    scope.submit = {
      text: null
    };

    if (window && window.appConfig.apiHost) {
      // fetch some stuff from the server:
      http.get(API_ROOT + '/items').then(function(response) {
        scope.items.length = 0;
        response.data.forEach(function(item) {
          scope.items.push(item);
        });
      });
    }

    scope.addItem = function(item) {
      scope.items.push(item);
    };

    scope.removeItem = function(item) {
      var idx = scope.items.indexOf(item);
      if (idx >= 0) {
        scope.items.splice(idx, 1);
      }
    };

    scope.submitToServer = function() {
      http.post(API_ROOT + '/submit', scope.submit).then(function(response) {
        scope.submitResponse = response.data;
      });
    };
  };

  module.exports = ['$scope', '$http', '$window', AppController];
})();
