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
 * CONSEQUENTIaL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.opalj
package concurrent

import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray
//import java.util.concurrent.ConcurrentLinkedQueue
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSpec
import org.scalatest.Matchers

import scala.concurrent.ExecutionContext

/**
 * Tests [[Tasks]].
 *
 * @author Michael Eichberg
 */
@RunWith(classOf[JUnitRunner])
class TasksTest extends FunSpec with Matchers {

    final val ThreadPool = ThreadPoolN(128)
    implicit final val TestExecutionContext = ExecutionContext.fromExecutorService(ThreadPool)

    describe("the Tasks control abstraction") {

        it("it should be possible to process an empty set of tasks") {
            val counter = new AtomicInteger(0)
            val exceptions = Tasks { (tasks: Tasks[Int], i: Int) ⇒ counter.incrementAndGet() }.join()
            exceptions should be('empty)
            counter.get should be(0)
        }

        it("it should be possible process a single task") {
            val counter = new AtomicInteger(0)
            val tasks = Tasks { (tasks: Tasks[Int], i: Int) ⇒
                counter.incrementAndGet()
                Thread.sleep(50)
            }
            tasks.submit(1)
            val exceptions = tasks.join()
            exceptions should be('empty)
            counter.get should be(1)
        }

        it("it should be possible to add a task while processing a task") {
            val counter = new AtomicInteger(0)
            val tasks: Tasks[Int] = Tasks { (tasks: Tasks[Int], i: Int) ⇒
                counter.incrementAndGet()
                if (i == 1) { tasks.submit(2) }
                Thread.sleep(50)
            }
            tasks.submit(1)
            val exceptions = tasks.join()
            exceptions should be('empty)
            counter.get should be(2)
        }

        it("it should be possible to create thousands of tasks while processing a task") {
            val processedValues = new AtomicIntegerArray(100000)
            val tasks: Tasks[Int] = Tasks { (tasks: Tasks[Int], i: Int) ⇒
                if (processedValues.addAndGet(i, 1) != 1) {
                    fail(s"the value $i was already processed")
                }
                if (i == 0) {
                    for (i ← 1 until 100000) tasks.submit(i)
                } else {
                    Thread.sleep(1)
                }
            }
            tasks.submit(0)
            val exceptions = tasks.join()
            exceptions should be('empty)
            for (i ← 0 until 100000) processedValues.get(i) should be(1)
        }

        it("it should be possible to submit tasks with a significant delay") {
            val processedValues = new AtomicInteger(0)
            val tasks: Tasks[Int] = Tasks { (tasks: Tasks[Int], i: Int) ⇒
                Thread.sleep(100)
                processedValues.incrementAndGet()
            }
            tasks.submit(1)
            Thread.sleep(250) // ttask 1 is probably already long finished...
            tasks.submit(2)
            val exceptions = tasks.join() // task 2 is probably still running..
            processedValues.get() should be(2)
            exceptions should be('empty)
        }

        it("it should be possible to create thousands of tasks in multiple steps multiple times") {
            for { r ← 1 to 3 } {
                val processedValues = new AtomicInteger(0)
                val subsequentlyScheduled = new AtomicInteger(0)
                val nextValue = new AtomicInteger(100000)
                val tasks: Tasks[Int] = Tasks { (tasks: Tasks[Int], i: Int) ⇒
                    processedValues.incrementAndGet()
                    if ((i % 1000) == 0) {
                        for (t ← 1 until 10) {
                            subsequentlyScheduled.incrementAndGet()
                            tasks.submit(nextValue.incrementAndGet())
                        }
                    } else {
                        Thread.sleep(1)
                    }
                }
                for (i ← 0 until 100000) tasks.submit(i)
                val exceptions = tasks.join()
                exceptions should be('empty)
                processedValues.get() should be(100000 + subsequentlyScheduled.get)
                info(s"run $r succeeded")
            }
        }

        it("it should be possible to create thousands of tasks in multiple steps even if some exceptions are thrown") {
            val processedValues = new AtomicInteger(0)
            val subsequentlyScheduled = new AtomicInteger(0)
            val aborted = new AtomicInteger(0)

            val nextValue = new AtomicInteger(100000)
            val tasks: Tasks[Int] = Tasks { (tasks: Tasks[Int], i: Int) ⇒
                if ((i % 1000) == 0) {
                    for (i ← 1 until 10) {
                        subsequentlyScheduled.incrementAndGet()
                        tasks.submit(nextValue.incrementAndGet())
                    }
                } else if ((i % 1333 == 0)) {
                    aborted.incrementAndGet()
                    throw new Exception();
                } else {
                    Thread.sleep(1)
                }
                processedValues.incrementAndGet()
            }
            for (i ← 0 until 100000) tasks.submit(i)

            val exceptions = tasks.join()

            info("subsequently scheduled: "+subsequentlyScheduled.get)
            info("number of caught exceptions: "+exceptions.size)

            exceptions.size should be(aborted.get)
            processedValues.get() should be(100000 + subsequentlyScheduled.get - aborted.get)
        }
    }
}
