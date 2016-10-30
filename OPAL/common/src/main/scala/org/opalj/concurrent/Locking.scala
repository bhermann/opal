/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2016
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
package concurrent

import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock
import org.opalj.collection.immutable.Chain
import org.opalj.collection.immutable.Naught

/**
 * A basic facility to model shared and exclusive access to some functionality/data
 * structure.
 *
 * ==Usage==
 * To use this generic locking facility you should mix in this trait.
 *
 * @author Michael Eichberg
 */
trait Locking {

    private[this] val rwLock = new ReentrantReadWriteLock()

    /**
     * Acquires the write lock associated with this instance and then executes
     * the function `f`. Afterwards, the lock is released.
     */
    protected[this] def withWriteLock[B](f: ⇒ B): B = Locking.withWriteLock(rwLock)(f)

    /**
     * Acquires the read lock associated with this instance and then executes
     * the function `f`. Afterwards, the lock is released.
     */
    protected[this] def withReadLock[B](f: ⇒ B): B = Locking.withReadLock(rwLock)(f)
}
/**
 * Defines several convenience methods related to using `(Reentrant(ReadWrite))Lock`s.
 */
object Locking {

    /**
     * Acquires the write lock associated with this instance and then executes
     * the function `f`. Afterwards, the lock is released.
     */
    @inline final def withWriteLock[B](rwLock: ReentrantReadWriteLock)(f: ⇒ B): B = {
        val lock = rwLock.writeLock()
        var isLocked = false
        try {
            lock.lock()
            isLocked = true
            f
        } finally {
            if (isLocked) lock.unlock()
        }
    }

    /**
     * Acquires all given locks in the given order and then executes the given function `f`.
     * Afterwards all locks are released in reverse order.
     */
    @inline final def withWriteLocks[T](
        rwLocks: TraversableOnce[ReentrantReadWriteLock]
    )(
        f: ⇒ T
    ): T = {
        var acquiredRWLocks: Chain[WriteLock] = Naught
        var error: Throwable = null
        val allLocked =
            rwLocks.forall { rwLock ⇒
                try {
                    val l = rwLock.writeLock
                    l.lock
                    acquiredRWLocks :&:= l
                    true
                } catch {
                    case t: Throwable ⇒
                        error = t
                        false
                }
            }

        assert(allLocked || (error ne null))

        try {
            if (allLocked) {
                f
            } else {
                // if we are here, something went so terribly wrong, that the performance
                // penalty of throwing an exception and immediately catching it, is a no-brainer...
                throw error;
            }
        } finally {
            acquiredRWLocks foreach { rwLock ⇒
                try { rwLock.unlock() } catch { case t: Throwable ⇒ if (error eq null) error = t }
            }
            if (error ne null) throw error;
        }
    }

    /**
     * Acquires the read lock and then executes
     * the function `f`. Before returning the lock is always released.
     */
    @inline final def withReadLock[B](rwLock: ReentrantReadWriteLock)(f: ⇒ B): B = {
        val lock = rwLock.readLock()
        try {
            lock.lock()
            f
        } finally {
            lock.unlock()
        }
    }

    /**
     * Tries to acquire the read lock and then executes
     * the function `f`; if the read lock cannot be acquired
     * the given function `f` is not executed and `None` is returned.
     *
     * If lock was acquired, it will always be released before the method returns.
     */
    final def tryWithReadLock[B](rwLock: ReentrantReadWriteLock)(f: ⇒ B): Option[B] = {
        var isLocked = false
        try {
            isLocked = rwLock.readLock().tryLock(100L, TimeUnit.MILLISECONDS)
            if (isLocked)
                Some(f)
            else
                None
        } finally {
            if (isLocked) rwLock.readLock().unlock()
        }
    }

    /**
     * Acquires the lock and then executes
     * the function `f`. Before returning the lock is always released.
     */
    @inline final def withLock[B](lock: ReentrantLock)(f: ⇒ B): B = {
        try {
            lock.lock()
            f
        } finally {
            lock.unlock()
        }
    }
}
