/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.test.instrumentation;

import org.junit.Test;

import com.oracle.truffle.js.nodes.instrumentation.JSTags.BinaryExpressionTag;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.LiteralExpressionTag;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.ReadPropertyExpressionTag;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.WritePropertyExpressionTag;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.WriteVariableExpressionTag;

public class IncDecOperationTest extends FineGrainedAccessTest {

    @Test
    public void inc() {
        evalAllTags("var a = 42; a++;");

        assertGlobalVarDeclaration("a", 42);

        // Inc operation de-sugared to a = a + 1;
        enter(WritePropertyExpressionTag.class, (e, write) -> {
            assertAttribute(e, KEY, "a");
            write.input(assertJSObjectInput);
            enter(BinaryExpressionTag.class, (e1, bin) -> {
                assertAttribute(e1, OPERATOR, "+");
                // read lhs global 'a'
                enter(ReadPropertyExpressionTag.class, (e2, p) -> {
                    assertAttribute(e2, KEY, "a");
                    p.input(assertGlobalObjectInput);
                }).exit(assertReturnValue(42));
                bin.input(42);
                // read rhs '1'
                enter(LiteralExpressionTag.class).exit(assertReturnValue(1));
                bin.input(1);
            }).exit();
            write.input(43);
        }).exit();
    }

    @Test
    public void dec() {
        evalAllTags("var a = 42; a--;");

        assertGlobalVarDeclaration("a", 42);

        // Dec operation de-sugared to tmp = tmp - 1;
        enter(WritePropertyExpressionTag.class, (e, write) -> {
            assertAttribute(e, KEY, "a");
            write.input(assertJSObjectInput);
            enter(BinaryExpressionTag.class, (e1, bin) -> {
                assertAttribute(e1, OPERATOR, "-");
                // read lhs global 'a'
                enter(ReadPropertyExpressionTag.class, (e2, p) -> {
                    assertAttribute(e2, KEY, "a");
                    p.input(assertGlobalObjectInput);
                }).exit(assertReturnValue(42));
                bin.input(42);
                // read rhs '1'
                enter(LiteralExpressionTag.class).exit(assertReturnValue(1));
                bin.input(1);
            }).exit();
            write.input(41);
        }).exit();
    }

    @Test
    public void decProperty() {
        evalWithTag("var a = {x:42}; a.x--;", BinaryExpressionTag.class);

        enter(BinaryExpressionTag.class, (e, b) -> {
            assertAttribute(e, OPERATOR, "-");
            b.input(42);
            b.input(1);
        }).exit();
    }

    @Test
    public void incDecVar() {
        evalWithTag("function foo(a){var b = 0; b+=a.x;}; foo({x:42});", WriteVariableExpressionTag.class);

        enter(WriteVariableExpressionTag.class, (e, b) -> {
            assertAttribute(e, NAME, "b");
            b.input(0);
        }).exit();
        enter(WriteVariableExpressionTag.class, (e, b) -> {
            assertAttribute(e, NAME, "b");
            b.input(42);
        }).exit();
    }

}
