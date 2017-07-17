/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2017
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
package ai;

import java.lang.Cloneable;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;

/**
 * Methods that perform various kinds of type checking and casting.
 *
 * @author Michael Eichberg
 */
public class MethodsWithTypeChecks {

    public static java.util.List<Integer> get() {
        if (System.currentTimeMillis() > 0)
            return null;
        else
            return new java.util.ArrayList<Integer>();
    }

    @SuppressWarnings("unchecked")
    public static java.util.Collection<Object> castToCollection(Object o) {
        return (java.util.Collection<Object>) o;
    }

    @SuppressWarnings("unchecked")
    public static java.util.Set<Object> castToSet(Object o) {
        return (java.util.Set<Object>) o;
    }

    public static Object castToObject(Object o){return o;}

    public static Object onceUselessOnceUsefulCast(IOException o) {
        IOException fioe = null;
        if(o == null)
            fioe = new FileNotFoundException();
        else
            fioe = o;


        return (FileNotFoundException)fioe;
    }

    public static Object exceptionsAndNull(IOException o) throws Exception {
        try {
            throw o;
        } catch (NullPointerException npe) {
            System.out.println(o /*<=> null*/);
            return npe;
        }
    }

    public static FileNotFoundException requiredCast(Cloneable o) {
        if(!(o instanceof FileNotFoundException)) return null;

        return (FileNotFoundException)o;
    }

    public static FileNotFoundException catchFailingCast(Object o) {
        try {
            return (FileNotFoundException)o;
        } catch (ClassCastException cce) {
            System.out.println(cce);
            return null;
        }
    }

    public static FileNotFoundException explicitTypeCheckAndCast(Object o) {
        try {

            if(! (o instanceof FileNotFoundException) )
                throw new ClassCastException("expected FileNotFoundException");

            return (FileNotFoundException)o;

        } catch (ClassCastException cce) {
            System.out.println(cce);
            return null;
        }
    }

    @SuppressWarnings("cast")
    public static void main(String[] args) {

        java.util.List<Integer> l = get(); // <= effectively always "null"

        System.out.println(l instanceof java.util.List);
        System.out.println(null instanceof Object);
        System.out.println(l instanceof Object);
        System.out.println(l instanceof java.util.Set<?>);
        System.out.println(l instanceof File);

        java.util.Collection<Object> colL = castToCollection(l);
        java.util.Set<Object> setL = castToSet(colL);
        System.out.println(castToObject(setL) instanceof File);

        Object o = l;
        IOException ioe = (IOException) o;
        System.out.println(ioe);
        System.out.println(ioe instanceof IOException);
        java.util.List<?> list = (java.util.List<?>) o;
        System.out.println(list instanceof java.util.List<?>);

        System.out.println("End of type frenzy.");
    }
}
