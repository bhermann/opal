/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2014
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
package util

import org.opalj.concurrent.Locking
import scala.collection.mutable.Map
import org.opalj.log.OPALLogger
import org.opalj.log.GlobalLogContext

/**
 * Measures the execution time of some code.
 *
 * ==Thread Safety==
 * This class is thread safe.
 *
 * @author Michael Eichberg
 */
class PerformanceEvaluation extends Locking {

    private[this] val timeSpans: Map[Symbol, Nanoseconds] = Map.empty

    /**
     * Times the execution of the given method / function literal / code block and
     * adds it to the execution time of previous methods / function literals/ code blocks
     * that were measured and for which the same symbol was used. <br/>
     * E.g. <code>time('base_analysis){ ... do something ... }</code>
     *
     * @param s Symbol used to put multiple measurements into relation.
     * @param f The function that will be evaluated and for which the execution
     *      time will be measured.
     */
    final def time[T](s: Symbol)(f: ⇒ T): T = {
        val startTime = System.nanoTime
        try {
            f
        } finally {
            val endTime = System.nanoTime
            withWriteLock { doUpdateTimes(s, new Nanoseconds(endTime - startTime)) }
        }
    }

    /**
     * Called by the `time` method.
     *
     * ==Thread Safety==
     * The `time` method takes care of the synchronization.
     */
    protected[this] def doUpdateTimes(s: Symbol, timeSpan: Nanoseconds): Unit = {
        val oldTimeSpan = timeSpans.getOrElseUpdate(s, Nanoseconds.None)
        timeSpans.update(s, oldTimeSpan + timeSpan)
    }

    /**
     * Returns the overall time spent by computations with the given symbol.
     */
    final def getTime(s: Symbol): Nanoseconds = withReadLock { doGetTime(s) }

    /**
     * Called by the `getTime(Symbol)` method.
     *
     * ==Thread Safety==
     * The `getTime` method takes care of the synchronization.
     */
    protected[this] def doGetTime(s: Symbol): Nanoseconds = {
        timeSpans.getOrElse(s, Nanoseconds.None)
    }

    /**
     * Resets the overall time spent by computations with the given symbol.
     */
    final def reset(s: Symbol): Unit = withWriteLock { doReset(s) }

    /**
     * Called by the `reset(Symbol)` method.
     *
     * ==Thread Safety==
     * The `reset` method takes care of the synchronization.
     */
    protected[this] def doReset(s: Symbol): Unit = timeSpans.remove(s)

    /**
     * Resets everything. The effect is comparable to creating a new
     * `PerformanceEvaluation` object, but is a bit more efficient.
     */
    final def resetAll(): Unit = withWriteLock { doResetAll() }

    /**
     * Called by the `resetAll` method.
     *
     * ==Thread Safety==
     * The `resetAll` method takes care of the synchronization.
     */
    protected[this] def doResetAll(): Unit = timeSpans.clear()

}
/**
 * Collection of helper functions useful when evaluating the performance of some
 * code.
 *
 * @author Michael Eichberg
 */
object PerformanceEvaluation {

    def avg(ts: Traversable[Nanoseconds]): Nanoseconds = {
        Nanoseconds(ts.map(_.timeSpan).sum / ts.size)
    }

    /**
     * Converts the specified number of bytes into the corresponding nubmer of mega bytes
     * and returns a textual representation.
     */
    def asMB(bytesCount: Long): String = {
        val mbs = bytesCount / 1024.0d / 1024.0d
        f"$mbs%.2f MB" // String interpolation
    }

    /**
     * Converts the specified number of nanoseconds into milliseconds.
     */
    final def ns2ms(nanoseconds: Long): Double =
        nanoseconds.toDouble / 1000.0d / 1000.0d

    /**
     * Measures the amount of memory that is used as a side-effect
     * of executing the given function `f`. I.e., the amount of memory is measured that is
     * used before and after executing `f`; i.e., the permanent data structures that are created
     * by `f` are measured.
     *
     * @note If large data structures are used by `f` that are
     * 		not used anymore afterwards then it may happen that the used amount of memory
     * 		is negative.
     */
    def memory[T](f: ⇒ T)(mu: Long ⇒ Unit): T = {
        val memoryMXBean = java.lang.management.ManagementFactory.getMemoryMXBean
        memoryMXBean.gc(); System.gc()
        val usedBefore = memoryMXBean.getHeapMemoryUsage.getUsed
        val r = f
        memoryMXBean.gc(); System.gc()
        val usedAfter = memoryMXBean.getHeapMemoryUsage.getUsed
        mu(usedAfter - usedBefore)
        r
    }

    /**
     * Times the execution of a given function `f`.
     *
     * @param r A function that is passed the time (in nanoseconds) that it
     *      took to evaluate `f`. `r` is called even if `f` fails with an exception.
     */
    def time[T](f: ⇒ T)(r: Nanoseconds ⇒ Unit): T = {
        val startTime: Long = System.nanoTime
        val result =
            try {
                f
            } finally {
                val endTime: Long = System.nanoTime
                r(Nanoseconds.TimeSpan(startTime, endTime))
            }
        result
    }

    /**
     * Times the execution of a given function `f` until the execution time has
     * stabilized and the average time for evaluating `f` is only changing in a
     * well-defined manner.
     *
     * In general `time` repeats the execution of `f` as long as the average changes
     * significantly. Furthermore, `f` is executed at least `minimalNumberOfRelevantRuns`
     * times and only those runs are taken into consideration for the calculation of the
     * average that are `consideredRunsEpsilon`% worse than the best run. However, if we
     * have more than `10*minimalNumberOfRelevantRuns` runs that did not contribute
     * to the calculation of the average, the last run is added anyway. This way, we
     * ensure that the evaluation will more likely terminate in reasonable time without
     * affecting the average too much. Nevertheless, if the behavior of `f` is
     * extremely eratic, the evaluation may not terminate.
     *
     * ==Example Usage==
     * {{{
     * import org.opalj.util.PerformanceEvaluation._
     * import org.opalj.util._
     *
     * import org.opalj.util.PerformanceEvaluation._
     * import org.opalj.util._
     * time[String](2,4,3,{Thread.sleep(300).toString}){ (t, ts) =>
     *            val sTs = ts.map(t => f"${t.toSeconds.timeSpan}%1.4f").mkString(", ")
     *            println(f"Avg: ${avg(ts).timeSpan}%1.4f; T: ${t.toSeconds.timeSpan}%1.4f; Ts: $sTs")
     * }
     * }}}
     *
     * @note **If `f` has side effects it may not be possible to use this method.**
     *
     * @note If epsilon is too small we can get an endless loop as the termination
     *      condition is never met. However, in practice often a value such as "1 or 2"
     *      is still useable.
     *
     * @note This method can generally only be used to measure the time of some process
     *      that does not require user interaction or disk/network access. In the latter
     *      case the variation between two runs will be too coarse grained to get
     *      meaningful results.
     *
     * @param epsilon The maximum percentage that *the final run* is allowed to affect
     *      the average. In other words,
     *      if the effect of the last execution on the average is less than `epsilon`
     *      percent. The evaluation halts and the result of the last run is returned.
     * @param consideredRunsEpsilon Controls which runs are taken into consideration
     *      when calculating the average. Only those runs are used that are at most
     *      `consideredRunsEpsilon%` slower than the last run. Additionally,
     *      the last run is only considered if it is at most `consideredRunsEpsilon%`
     *      slower than the average. Hence, it is even possible that the average may rise
     *      during the evaluation of `f`.
     * @param f The function that will be measured.
     * @param r A function that is called back whenever `f` was successfully evaluated.
     *      The signature is:
     *      {{{
     *      def r(lastExecutionTime:Nanoseconds, consideredExecutionTimes : Seq[Nanoseconds]) : Unit
     *      }}}
     *       1. The first parameter is the last execution time of `f`.
     *       1. The last parameter are the times of the evaluation of `f` that are taken
     *      into consideration when calculating the average.
     */
    def time[T >: Null <: AnyRef](
        epsilon:                     Int,
        consideredRunsEpsilon:       Int,
        minimalNumberOfRelevantRuns: Int,
        f:                           ⇒ T
    )(
        r: (Nanoseconds, Seq[Nanoseconds]) ⇒ Unit
    ): T = {

        require(minimalNumberOfRelevantRuns >= 3)
        require(consideredRunsEpsilon > epsilon)

        var result: T = null

        val e = epsilon.toDouble / 100.0d
        val filterE = (consideredRunsEpsilon + 100).toDouble / 100.0d

        var runsSinceLastUpdate = 0
        var times = List.empty[Nanoseconds]
        time { f } { t ⇒
            times = t :: times
            if (t.timeSpan <= 199999) { // < 2 milliseconds
                r(t, times)
                OPALLogger.warn(
                    "common",
                    s"the time required by the function (${t.toString}) "+
                        "is too small to get meaningful measurements."
                )(GlobalLogContext)

                return result;
            }
        }
        var avg: Double = times.head.timeSpan.toDouble
        do {
            time {
                result = f
            } { t ⇒
                if (t.timeSpan <= avg * filterE) {
                    // let's throw away all runs that are significantly slower than the last run
                    times = t :: times.filter(_.timeSpan <= t.timeSpan * filterE)
                    avg = times.map(_.timeSpan).sum.toDouble / times.size.toDouble
                    runsSinceLastUpdate = 0
                } else {
                    runsSinceLastUpdate += 1
                    if (runsSinceLastUpdate > minimalNumberOfRelevantRuns * 2) {
                        // for whatever reason the current average seems to be "too" slow
                        // let's add the last run to rise the average
                        times = t :: times
                        avg = times.map(_.timeSpan).sum.toDouble / times.size.toDouble
                        runsSinceLastUpdate = 0
                    }
                }
                r(t, times)
            }
        } while (times.size < minimalNumberOfRelevantRuns ||
            Math.abs(avg - times.head.timeSpan) > avg * e)

        result
    }

    /**
     * Times the execution of a given function `f`.
     *
     * @param r A function that is passed the time that it
     *      took to evaluate `f` and the result produced by `f`.
     *      `r` is only called if `f` succeeds.
     */
    def run[T, X](f: ⇒ T)(r: (Nanoseconds, T) ⇒ X): X = {
        val startTime: Long = System.nanoTime
        val result = f
        val endTime: Long = System.nanoTime
        r(Nanoseconds.TimeSpan(startTime, endTime), result)
    }
}
