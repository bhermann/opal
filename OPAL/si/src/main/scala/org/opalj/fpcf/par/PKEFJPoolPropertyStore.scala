/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package fpcf
package par

import java.util.concurrent.ForkJoinPool

import java.util.concurrent.TimeUnit

import org.opalj.log.LogContext

/**
 * A concurrent implementation of the property store which executes the scheduled computations
 * in parallel using a ForkJoinPool.
 *
 * We use `NumberOfThreadsForProcessingPropertyComputations` threads for processing the
 * scheduled computations.
 *
 * @author Michael Eichberg
 */
final class PKEFJPoolPropertyStore private (
        val ctx:                                              Map[Class[_], AnyRef],
        val NumberOfThreadsForProcessingPropertyComputations: Int
)(
        implicit
        val logContext: LogContext
) extends PKECPropertyStore { store ⇒

    private[this] val pool: ForkJoinPool = {
        new ForkJoinPool(
            NumberOfThreadsForProcessingPropertyComputations,
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            (_: Thread, e: Throwable) ⇒ { collectException(e) },
            false
        )
    }

    override protected[this] def awaitPoolQuiescence(): Unit = handleExceptions {
        if (!pool.awaitQuiescence(Long.MaxValue, TimeUnit.DAYS)) {
            throw new UnknownError("pool failed to reach quiescence")
        }
        // exceptions that are thrown in a pool's thread are "only" collected and, hence,
        // may not "immediately" trigger the termination.
        if (exception != null) throw exception;
    }

    override def shutdown(): Unit = pool.shutdown()

    override def isIdle: Boolean = pool.isQuiescent

    override protected[this] def parallelize(r: Runnable): Unit = pool.execute(r)

    override protected[this] def forkPropertyComputation[E <: Entity](
        e:  E,
        pc: PropertyComputation[E]
    ): Unit = {
        pool.execute(() ⇒ {
            if (doTerminate) throw new InterruptedException();
            store.processResult(pc(e))
        })
        incrementScheduledTasksCounter()
    }

    override protected[this] def forkResultHandler(r: PropertyComputationResult): Unit = {
        pool.execute(() ⇒ {
            if (doTerminate) throw new InterruptedException();
            store.processResult(r)
        })
        incrementScheduledTasksCounter()
    }

    override protected[this] def forkOnUpdateContinuation(
        dependerEPK: SomeEPK,
        e:           Entity,
        pk:          SomePropertyKey
    ): Unit = {
        pool.execute(() ⇒ {
            if (doTerminate) throw new InterruptedException();
            val dependerState = properties(dependerEPK.pk.id).get(dependerEPK.e)
            val c = dependerState.getAndClearOnUpdateComputation(dependerEPK)
            if (c != null) {
                // get the newest value before we actually call the onUpdateContinuation
                val newEPS = store(e, pk).asEPS
                // IMPROVE ... see other forkOnUpdateContinuation
                store.processResult(c(newEPS))
            }
        })
        incrementScheduledTasksCounter()
    }

    override protected[this] def forkOnUpdateContinuation(
        c:       OnUpdateContinuation,
        finalEP: SomeFinalEP
    ): Unit = {
        pool.execute(() ⇒ {
            if (doTerminate) throw new InterruptedException();
            // IMPROVE: Instead of naively calling "c" with finalEP, it may be worth considering which other updates have happened to figure out which update may be the "beste"
            store.processResult(c(finalEP))
        })
        incrementScheduledTasksCounter()
    }

}

object PKEFJPoolPropertyStore extends ParallelPropertyStoreFactory {

    def apply(
        context: PropertyStoreContext[_ <: AnyRef]*
    )(
        implicit
        logContext: LogContext
    ): PKECPropertyStore = {
        val contextMap: Map[Class[_], AnyRef] = context.map(_.asTuple).toMap
        new PKEFJPoolPropertyStore(contextMap, NumberOfThreadsForProcessingPropertyComputations)
    }

    def create(
        context: Map[Class[_], AnyRef] // ,PropertyStoreContext[_ <: AnyRef]*
    )(
        implicit
        logContext: LogContext
    ): PKECPropertyStore = {

        new PKEFJPoolPropertyStore(context, NumberOfThreadsForProcessingPropertyComputations)
    }

}

