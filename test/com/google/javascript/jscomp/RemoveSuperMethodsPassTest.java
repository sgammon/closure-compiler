/*
 * Copyright 2016 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.javascript.jscomp;

import com.google.common.collect.ImmutableList;

/** Tests for {@link RemoveSuperMethodsPass} */
public final class RemoveSuperMethodsPassTest extends CompilerTestCase {

  private static final String BOILERPLATE =
      lines(
          "/** @constructor */",
          "var FooBase = function() {};",
          "FooBase.prototype.bar = function() {};",
          "/** @param {number} time */",
          "/** @param {number} loc */",
          "/** @return {number} */",
          "FooBase.prototype.baz = function(time, loc) { return 2; }",
          "/** @param {number} time */",
          "/** @param {number=} opt_loc */",
          "FooBase.prototype.buzz = function(time, opt_loc) {} ",
          "/** @param {...number} var_args */",
          "FooBase.prototype.var = function(var_args) {} ",
          "/** @constructor @extends {FooBase} */",
          "var Foo = function() {}",
          "Foo.superClass_ = FooBase.prototype",
          "/** @const */ var ns = {};",
          "/** @constructor */ ns.FooBase = function() {};",
          "ns.FooBase.prototype.bar = function() {};",
          "/** @constructor @extends {ns.FooBase} */ ns.Foo = function() {};",
          "ns.Foo.superClass_ = ns.FooBase.prototype");

  public RemoveSuperMethodsPassTest() {
    super(DEFAULT_EXTERNS);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    enableTypeCheck();
  }

  @Override
  protected CompilerPass getProcessor(final Compiler compiler) {
    return new RemoveSuperMethodsPass(compiler);
  }

  private void testOptimize(String code) {
    test(lines(BOILERPLATE, code), BOILERPLATE);
  }

  private void testNoOptimize(String code) {
    testSame(lines(BOILERPLATE, code));
  }

  public void testOptimize_noArgs() {
    testOptimize(
        lines(
            "/** @override */",
            "Foo.prototype.bar = function() { Foo.superClass_.bar.call(this); };"));
  }

  public void testOptimize_noArgs_baseClassName() {
    testOptimize(
        lines(
            "/** @override */",
            "Foo.prototype.bar = function() { FooBase.prototype.bar.call(this) };"));
  }

  public void testOptimize_noArgs_namespace() {
    testOptimize(
        lines(
            "/** @override */",
            "ns.Foo.prototype.bar = function() { ns.Foo.superClass_.bar.call(this); };"));
  }

  public void testOptimize_noArgs_baseClassName_namespace() {
    testOptimize(
        lines(
            "/** @override */",
            "ns.Foo.prototype.bar = function() { ns.FooBase.prototype.bar.call(this); };"));
  }

  public void testOptimize_twoArgs() {
    testOptimize(
        lines(
            "/** @override */",
            "Foo.prototype.baz = function(time, loc) {",
            "  return Foo.superClass_.baz.call(this, time, loc);",
            "};"));
  }

  public void testOptimize_varArgs() {
    testOptimize(
        lines(
            "/** @override */",
            "Foo.prototype.var = function(var_args) {",
            "  return Foo.superClass_.var.call(this, var_args);",
            "};"));
  }

  public void testNoOptimize_numArgsMismatch() {
    testNoOptimize(
        lines(
            "/** @override */",
            "Foo.prototype.buzz = function(time, opt_loc) {",
            "  return Foo.superClass_.buzz.call(this, time);",
            "};"));
  }

  public void testNoOptimize_notBaseClass() {
    testNoOptimize(
        lines(
            "/** @override */",
            "Foo.prototype.bar = function() { FooBaz.prototype.bar.call(this) };"));
  }

  public void testNoOptimize_argOrderMismatch() {
    testNoOptimize(
        lines(
            "/** @override */",
            "Foo.prototype.baz = function(time, loc) {",
            "  return Foo.superClass_.baz.call(this, loc, time);",
            "};"));
  }

  public void testNoOptimize_argNameMismatch() {
    testNoOptimize(
        lines(
            "/** @override */",
            "Foo.prototype.baz = function(time, loc) {",
            "  return Foo.superClass_.baz.call(this, time + 2, loc);",
            "};"));
  }

  public void testNoOptimize_methodNameMismatch() {
    testNoOptimize(
        lines(
            "/** @override @suppress {checkTypes} */",
            "Foo.prototype.baz = function(time, loc) {",
            "  return Foo.superClass_.buzz.call(this, time, loc);",
            "};"));

    testNoOptimize(
        lines(
            "Foo.baw = { prototype: { baz: function(time, loc) {} }};",
            "/** @override */",
            "Foo.baw.prototype.baz = function(time, loc) {",
            "  return Foo.superClass_.baz.call(this, time, loc);",
            "};"));

    testNoOptimize(
        lines(
            "Foo.prototype.baw = {",
            "  superClass_: { baz: /** @return {number} */ function(time, loc) { return 3; } }",
            "};",
            "/** @override */",
            "Foo.prototype.baz = function(time, loc) {",
            "  return Foo.prototype.baw.superClass_.baz.call(this, time, loc);",
            "};"));

    testNoOptimize(
        lines(
            "Foo.prototype.baw = {};",
            "Foo.prototype.baw.prototype = Foo.prototype;",
            "/** @override */",
            "Foo.prototype.baz = function(time, loc) {",
            "  return FooBase.prototype.baw.prototype.baz.call(this, time, loc);",
            "};"));
  }

  public void testNoOptimize_unsound_grandparentClass() {
    // It's technically unsound to remove a call to the grandparent class's method if that makes the
    // parent's override to be skipped.
    testNoOptimize(
        lines(
            "/** @constructor @extends {Foo} */",
            "var Bar = function() {}",
            "/** @override */",
            "Bar.prototype.baz = function(time, loc) {",
            "  return FooBase.prototype.baz.call(this, time, loc);",
            "};"));
  }

  public void testNoOptimize_moreThanOneStatement() {
    testNoOptimize(
        lines(
            "/** @override */",
            "Foo.prototype.baz = function(time, loc) {",
            "  this.bar();",
            "  return Foo.superClass_.baz.call(this, time, loc);",
            "};"));
  }

  public void testNoOptimize_missingReturn() {
    testNoOptimize(
        lines(
            "/** @override */",
            "Foo.prototype.baz = function(time, loc) {",
            "  FooBase.prototype.baz.call(this, time, loc);",
            "};"));
  }

  public void testNoOptimize_wizaction() {
    testNoOptimize(
        lines(
            "/** @override @wizaction */",
            "Foo.prototype.bar = function() { Foo.superClass_.bar.call(this); };"));
  }

  public void testNoOptimize_duplicate() {
    testSame(ImmutableList.of(
        SourceFile.fromCode("file1.js",
            lines(BOILERPLATE,
                "/** @override */",
                "Foo.prototype.bar = function() { Foo.superClass_.bar.call(this); };")),
        SourceFile.fromCode("file2.js",
            lines(
                "/** @override @suppress {duplicate} */",
                "Foo.prototype.bar = function() { Foo.superClass_.bar.call(this); };"))));
  }
}
