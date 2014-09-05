(function() {

  'use strict';

  var angular = require('angular');

  var services = angular.module('banker.services', []);
  var configSvc = require('./config-service');

  services.service('ConfigService', configSvc);

  module.exports = services;
})();
