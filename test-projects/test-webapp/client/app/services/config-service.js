(function() {
  'use strict';

  var ConfigService = function(http, q, window) {
    var appConfig = window.appConfig;

    var API_BASE_URL = appConfig.apiHost;

    this.API_BASE_URL = API_BASE_URL;

    var defered = q.defer();
    this.config = defered.promise;

    http.get(API_BASE_URL + '/config').then(function(conf) {
      defered.resolve(conf.data);
    });

  };

  module.exports = ['$http', '$q', '$window', ConfigService];
})();
