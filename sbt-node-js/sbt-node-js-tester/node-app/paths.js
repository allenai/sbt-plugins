var withIgnore = function(include) {
  return [include, '!app/**/*#*'];
};

/** Paths for the application build */
module.exports = {
  all: withIgnore('app/**/*'),
  scripts: withIgnore('app/**/*.js'),
  jsTest: withIgnore('app/**/*_test.js'),
  less: 'app/main.less',
  html: withIgnore('app/**/*.html'),
  appRoot: 'app',
  app: 'app/app.js',
  style: 'app/main.less', // the main LESS file
  tmp: 'tmp',
  tmpTests: 'tmp/tests',
  buildDev: 'build',
  buildProd: '../banker-server/public'
};
