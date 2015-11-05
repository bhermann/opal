/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2015
 * Software Technology Group
 * Department of Computer Science
 * Technische Universität Darmstadt
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.opalj.fp.analyses;

/**
 * Some Demo code to test/demonstrate the complexity related to calculating the purity of
 * methods in the presence of mutual recursive methods.
 * 
 * @author Michael Eichberg
 *
 */
class Demo {

    private static int myValue = -1; /* effectivelyFinal */

    private Demo() {/* empty */
    }

    public static int pureThoughItUsesField(int i, int j) {
        return i % j * myValue;
    }

    public static int pureThoughItUsesField2(int i, int j) {
        return i * j * myValue;
    }

    public static int simplyPure(int i, int j) {
        return i % 3 == 0 ? simplyPure(i, 0) : simplyPure(0, j);
    }

    public static int impure(int i) {
        return (int) (i * System.nanoTime());
    }

    //
    // The following two methods are mutually dependent and are pure.
    //
    static int foo(int i) {
        return i < 0 ? i : bar(i - 10);
    }

    static int bar(int i) {
        return i % 2 == 0 ? i : foo(i - 1);
    }

    // The following methods are not direct involved in a
    // mutually recursive dependency, but require information about a set of
    // mutually recursive dependent methods.
    static int fooBar(int i) { // also observed by other methods
        return foo(i) + bar(i);
    }

    static int barFoo(int i) {
        return foo(i) + bar(i); // not observed
    }

    // The following two methods are mutually dependent and use an impure method.
    //

    static int npfoo(int i) {
        return i < 0 ? simplyPure(i, 0) : npbar(i - 10);
    }

    static int npbar(int i) {
        return i % 2 == 0 ? impure(i) : foo(i - 1);
    }

    //
    // All three methods are actually pure but have a dependency on each other...
    //
    static int m1(int i) {
        return i < 0 ? i : m2(i - 10);
    }

    static int m2(int i) {
        return i % 2 == 0 ? i : m3(i - 1);
    }

    static int m3(int i) {
        return i % 4 == 0 ? i : m1(i - 1);
    }
    
    // All three methods are depending on each other, but they are NOT pure, because
    // one calls an impure method.
    //

    static int m1np(int i) {
        return i < 0 ? i : m2np(i - 10);
    }

    static int m2np(int i) {
        return i % 2 == 0 ? i : m3np(i - 1);
    }

    static int m3np(int i) {
        int k = m1(i);
        int j = m1np(k - 1);
        return impure(j);
    }

    // The following method is pure, but only if we know the pureness of the target method
    // which we don't know if do not analyze the JDK!
    //

    static int cpure(int i) {
        return Math.abs(i) * 21;
    }

    static int cpureCallee(int i) {
        return cpure(i / 21);
    }

    static int cpureCalleeCallee1(int i) {
        return cpureCallee(i / 21);
    }

    static int cpureCalleeCallee2(int i) {
        return cpureCallee(i / 21);
    }

    static int cpureCalleeCalleeCalle(int i) {
        return cpureCalleeCallee1(i / 21) * cpureCalleeCallee2(i / 21);
    }

    static int cpureCalleeCalleeCalleCallee(int i) {
        return cpureCalleeCalleeCalle(1299);
    }

    // All methods are involved in multiple cycles of dependent methods
    // one calls an impure method.
    //

    static int mm1(int i) {
        return i < 0 ? i : mm2(i - 10);
    }

    static int mm2(int i) {
        return i % 2 == 0 ? mm1(-i) : mm3(i - 1);
    }

    static int mm3(int i) {
        int j = m3(i);
        int k = mm2(j);
        return m1(k);
    }

    // Two cycles connected by a "weak link" (fooBar)
    //

    static int cm1(int i) {
        return i < 0 ? i : cm2(i - 10);
    }

    static int cm2(int i) {
        return i % 2 == 0 ? cm1(-i) : fooBar(i - 1);
    }

    // A classical strongly connected component
    //
    static int scc0(int i) {
        return i < 0 ? scc2(i - 10) : scc1(i - 111);
    }

    static int scc1(int i) {
        return i % 2 == 0 ? 32424 : scc3(i - 1);
    }

    static int scc2(int i) {
        return i % 2 == 0 ? 1001 : scc3(i - 3);
    }

    static int scc3(int i) {
        return scc0(12121 / i);
    }
}
