(function() {
  'use strict';

  var angular = require('angular');
  var appController = require('./app-controller');
  var services = require('./services/services');

  var app = angular.module('app', [
    services.name,
  ]);

  app.controller('AppController', appController);
})();
