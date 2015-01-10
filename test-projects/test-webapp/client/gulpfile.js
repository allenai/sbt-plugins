var gulp = require('gulp');

gulp.task('default', function() {
  gulp.src('app/**/*').pipe(gulp.dest('build'));
});