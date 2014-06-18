var tests = 'tmp/bundled_tests.js';

module.exports = function(config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine'],
    files: [tests],
    browsers: ['PhantomJS']
  });
};
