var withIgnore = function(include) {
  return [include, '!app/**/*#*'];
};

/** Paths for the application build */
module.exports = {
  all: withIgnore('app/**/*'),
  scripts: withIgnore('app/**/*.js'),
  jsTest: withIgnore('app/**/*_test.js'),
  less: withIgnore('app/**/*.less'),
  html: withIgnore('app/**/*.html'),
  appRoot: 'app',
  app: 'app/app.js',
  style: 'app/main.less', // the main LESS file
  tmp: 'tmp',
  bundledTests: 'tmp/bundled_tests.js',
  buildDev: 'build'
};
