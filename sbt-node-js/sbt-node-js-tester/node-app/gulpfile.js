var gulp = require('gulp');
var gutil = require('gulp-util');
var log = gutil.log;

var paths = require('./paths');

// Load some gulp plugins:
var browserify = require('gulp-browserify');
var uglify = require('gulp-uglify');
var concat = require('gulp-concat');
var jshint = require('gulp-jshint');
var less = require('gulp-less');
var clean = require('gulp-clean');
var livereload = require('gulp-livereload');
var embedlr = require("gulp-embedlr");
var karma = require('gulp-karma');
var gulpIf = require('gulp-if');
var insert = require('gulp-insert');

// jshint reporter:
var stylish = require('jshint-stylish');

// build environment, either 'dev' or 'prod'
var env = process.env.NODE_ENV = process.env.NODE_ENV || 'dev';
var buildDir = process.env.NODE_BUILD_DIR || paths.buildDev;

// change the host to whatever your server will be listening from
var apiHost = process.env.NODE_API_HOST || 'http://localhost:8080/api';

log("Build environment: " + env);
log("Build dir: " + buildDir);
log("API host: " + apiHost);

/** Generates a config object that can bee appended to the final app.js */
var generateConfig = function() {
  var config = {apiHost: apiHost};
  var appendString = '\nwindow.appConfig = ' + JSON.stringify(config) + ';';
  log("Config: ", appendString);
  return appendString;
};

// Needed or else the livereload fails after a while (due to a memory leak?)
// TODO(markschaake): find out why this is necessary
process.setMaxListeners(0);

/** Task for linting Javascript sources via JsHint library */
gulp.task('lint', function() {
  return gulp.src(paths.scripts)
    .pipe(jshint())
    .pipe(jshint.reporter(stylish));
});

/** Compile Javascript into a single file via Browserify */
gulp.task('js', ['lint'], function() {
  return gulp.src(paths.app)
    .pipe(browserify({
      insertGlobals : true,
      debug : env === 'dev' // enable source maps for dev mode
    }))
    // TODO: generate a global config object (i.e. window.AppConfig)
    // and read from that object at runtime instead of doing a find-replace
    .pipe(insert.append(generateConfig()))
    .pipe(gulpIf(env === 'prod', uglify()))
    .pipe(gulp.dest(buildDir));
});

/** Compile LESS into a single CSS file */
gulp.task('styles', function() {
  return gulp.src(paths.less)
    .pipe(less({paths: [ paths.style ]}))
    .pipe(concat('main.css'))
    .pipe(gulp.dest(buildDir));
});

gulp.task('html', function() {
  return gulp.src(paths.html)
    .pipe(gulpIf(env === 'dev', embedlr())) // embed the livereload script for dev
    .pipe(gulp.dest(buildDir));
});

gulp.task('build', ['js', 'styles', 'html']);

gulp.task('server', function(next) {
  var connect = require('connect'),
      server = connect();
  var port = process.env.PORT || 4000;
  log("Server will start on port: " + port);
  return server.use(connect.static(paths.buildDev)).listen(port, next);
});

gulp.task('clean', function() {
  return gulp.src([buildDir, paths.tmp], {read: false})
    .pipe(clean({force: true}));
});

gulp.task('browserify-tests', function() {
  return gulp.src(paths.jsTest)
    .pipe(browserify({
      insertGlobals : true,
      debug : false // disable source maps
    }))
    .pipe(gulp.dest(paths.tmpTests));
});

gulp.task('watch-browserified-tests', ['browserify-tests'], function() {
  return gulp.watch(paths.scripts, ['browserify-tests']);
});

var karmaTask = function(taskName, action, deps) {
  return gulp.task(taskName, deps, function() {
    return gulp.src(paths.tmpTests + "/**/*_test.js")
      .pipe(karma({
        configFile: 'karma.conf.js',
        action: action
      }))
      .on('error', function(err) {
        // Make sure failed tests cause gulp to exit non-zero
        throw err;
      });
  });
};

/** Run karama tests once */
karmaTask('test', 'run', ['browserify-tests']);

/** Run karma tests when files change */
karmaTask('test-watch', 'watch', ['watch-browserified-tests']);

gulp.task('default', ['server', 'build'], function() {
  var liveserver = livereload();

  // Watch sources, when they change force a new build
  gulp.watch(paths.all, ['build']);

  // On new build, notify the server
  gulp.watch(buildDir + '/**').on('change', function(file) {
    liveserver.changed(file.path);
  });
});
