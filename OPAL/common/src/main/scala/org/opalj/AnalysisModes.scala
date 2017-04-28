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
package org.opalj

/**
 * The analysis mode specifies the type of the project/the kind of sources which will be
 * analyzed and - in case of libraries – the underlying assumption that should be used.
 *
 * Basically an enumeration of the modes that can be used by the subsequent analyses.
 *
 * @author Michael Eichberg
 */
object AnalysisModes extends Enumeration {

    /**
     * This mode is to be used if a library is analyzed and the assumption is made
     * that other developers do not add classes/contribute to the packages of the
     * analyzed library and, hence, do not make direct use of package-visible functionality.
     *
     * It is recommended to use this mode when analyzing a library w.r.t. general
     * programming errors.
     */
    final val LibraryWithClosedPackagesAssumption = Value("library with closed packages assumption")

    final val CPA = LibraryWithClosedPackagesAssumption

    /**
     * This mode is to be used if a library is analyzed and the assumption is made
     * that other developers may by purpose/accidentally add classes to this
     * package and, hence, may make direct use of package-visible functionality.
     *
     * It is recommended to use this mode when analyzing a library w.r.t. security
     * issues.
     */
    final val LibraryWithOpenPackagesAssumption = Value("library with open packages assumption")

    final val OPA = LibraryWithOpenPackagesAssumption

    /**
     * This mode is to be used if a standard Java application is analyzed which is started by
     * the JVM by calling the application's main method.
     */
    final val DesktopApplication = Value("desktop application")
    final def isDesktopApplication(mode: AnalysisMode): Boolean = mode eq DesktopApplication

    /**
     * This mode is to be used if a web application is analyzed which is developed using JavaEE 6.
     */
    final val JEE6WebApplication = Value("JEE6 web application")
    final def isJEE6WebApplication(mode: AnalysisMode): Boolean = mode eq JEE6WebApplication

    final def isLibraryLike(mode: AnalysisMode): Boolean = {
        (mode eq CPA) || (mode eq OPA)
    }

    final def isApplicationLike(mode: AnalysisMode): Boolean = {
        (mode eq DesktopApplication) || (mode eq JEE6WebApplication)
    }

}

/**
 * Common constants related to the analysis mode.
 *
 * @note The package defines the type `AnalysisMode`.
 */
object AnalysisMode {
    final val ConfigKey = "org.opalj.analysisMode"
}
