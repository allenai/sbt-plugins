var foo = 1;

var bar = "abcs";

var baz = function() { return foo + bar; };

var fiz = function() { return bar + "1"; };

// Try removing the semicolon and see that jshint fails compile
baz();

// Change this comment
fiz();


