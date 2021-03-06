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
package com.oracle.truffle.js.nodes.binary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.nodes.Truncatable;
import com.oracle.truffle.js.nodes.access.JSConstantNode.JSConstantIntegerNode;
import com.oracle.truffle.js.nodes.cast.JSToUInt32Node;
import com.oracle.truffle.js.nodes.instrumentation.JSTags.BinaryExpressionTag;
import com.oracle.truffle.js.runtime.JSTruffleOptions;

/**
 * 11.7.3 The Unsigned Right Shift Operator (>>>).
 */
@NodeInfo(shortName = ">>>")
public abstract class JSUnsignedRightShiftNode extends JSBinaryNode {

    protected JSUnsignedRightShiftNode(JavaScriptNode left, JavaScriptNode right) {
        super(left, right);
    }

    @Child private JSToUInt32Node toUInt32Node;

    public static JavaScriptNode create(JavaScriptNode left, JavaScriptNode right) {
        Truncatable.truncate(left);
        Truncatable.truncate(right);
        if (JSTruffleOptions.UseSuperOperations && right instanceof JSConstantIntegerNode) {
            return JSUnsignedRightShiftConstantNode.create(left, right);
        }
        return JSUnsignedRightShiftNodeGen.create(left, right);
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        if (tag == BinaryExpressionTag.class) {
            return true;
        } else {
            return super.hasTag(tag);
        }
    }

    protected static boolean rvalZero(int b) {
        return (b & 0x1f) == 0;
    }

    @Specialization(guards = {"rvalZero(b)", "a >= 0"})
    protected int doIntegerFast(int a, @SuppressWarnings("unused") int b) {
        return a;
    }

    @Specialization(guards = "a >= 0")
    protected int doInteger(int a, int b) {
        return a >>> b;
    }

    @Specialization(guards = "!rvalZero(b)")
    protected int doIntegerNegative(int a, int b) {
        return a >>> b;
    }

    @Specialization(guards = "rvalZero(b)")
    protected double doDoubleZero(double a, @SuppressWarnings("unused") int b) {
        return toUInt32(a);
    }

    @Specialization(guards = "!rvalZero(b)")
    protected int doDouble(double a, int b) {
        return (int) (toUInt32(a) >>> (b & 0x1F));
    }

    @Specialization(guards = "!isHandled(lval, rval)")
    protected Object doGeneric(Object lval, Object rval,
                    @Cached("create()") JSToUInt32Node rvalToUint32Node,
                    @Cached("createBinaryProfile()") ConditionProfile returnType) {
        long lnum = toUInt32(lval);
        int shiftCount = (int) (rvalToUint32Node.executeLong(rval) & 0x1F);
        if (returnType.profile(lnum >= Integer.MAX_VALUE || lnum <= Integer.MIN_VALUE)) {
            return (double) (lnum >>> shiftCount);
        }
        return (int) (lnum >>> shiftCount);
    }

    private long toUInt32(Object target) {
        if (toUInt32Node == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            toUInt32Node = insert(JSToUInt32Node.create());
        }
        return toUInt32Node.executeLong(target);
    }

    protected static boolean isHandled(Object lval, Object rval) {
        return (lval instanceof Integer || lval instanceof Double) && rval instanceof Integer;
    }

    @Override
    public boolean isResultAlwaysOfType(Class<?> clazz) {
        return clazz == Number.class;
    }

    @Override
    protected JavaScriptNode copyUninitialized() {
        return JSUnsignedRightShiftNodeGen.create(cloneUninitialized(getLeft()), cloneUninitialized(getRight()));
    }
}
