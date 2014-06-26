(function() {
  var angular = require('angular');
  var appController = require('./app-controller');

  var app = angular.module('app', []);
  app.controller('AppController', appController);
})();
