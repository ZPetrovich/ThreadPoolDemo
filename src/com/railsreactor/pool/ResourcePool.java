package com.railsreactor.pool;

import java.util.concurrent.TimeUnit;

/**
 * Generic resource pool (i.e. a pool of instances of type R where R is a
 * generic type). The pool allows resources to be acquired (checked out of the
 * pool) and then later released (checked back into the pool). New resources may
 * be added to the pool, and existing resources may be removed from the pool.
 * <p>
 * The resource pool is thread-safe.
 * 
 * @author Michael Zayikin
 * 
 * @param <R>
 */
public interface ResourcePool<R> {

    /**
     * Opens pool.
     */
    void open();

    /**
     * Checks whether pool is open or not.
     * 
     * @return <code>true</code> is pool is opened; <code>false</code> otherwise
     */
    boolean isOpen();

    /**
     * Closes pool waiting before all acquired resources will be released.
     * <p>
     * 
     * <b>Note</b>, this method may be blocked forever unless all acquired
     * resources will be released!
     * 
     * @throws InterruptedException
     *             if interrupted while waiting
     */
    void close() throws InterruptedException;

    /**
     * Closes pool immediately.
     * <p>
     * <b>Note</b>, all acquired resources may not be released back to pool
     * after this method invocation.
     */
    void closeNow();

    /**
     * Acquires (checks out) resource from the pool.
     * <p>
     * Note, this method may be blocked until pool become not empty.
     * 
     * @return acquired resources
     * @throws InterruptedException
     *             if interrupted while waiting
     */
    R acquire() throws InterruptedException;

    /**
     * Acquires (checks out) resource from the pool waiting if needed for
     * resource availability maximum <code>timeout</code> for
     * <code>timeUnit</code>s. If a resource cannot be acquired within the
     * timeout interval specified in the acquire(long, TimeUnit) method a
     * <code>null</code> can be returned.
     * 
     * @param timeout
     *            the number of units to wait
     * @param timeUnit
     *            the unit of <code>timeout</code>
     * @return acquired resources or <code>null</code> if it can't be acquired
     * @throws InterruptedException
     *             if interrupted while waiting
     */
    R acquire(final long timeout, final TimeUnit timeUnit) throws InterruptedException;

    /**
     * Releases specified <code>resource</code> to this pool.
     * 
     * @param resource
     *            the resource to be released
     */
    void release(final R resource);

    /**
     * Adds specified <code>resource</code> to the pool.
     * 
     * @throws ClassCastException
     *             if the class of the specified <code>resource</code> prevents
     *             it from being added to this pool
     * @throws NullPointerException
     *             if <code>resource</code> is <code>null</code>
     * @throws IllegalArgumentException
     *             if some property of the specified <code>resource</code>
     *             prevents it from being added to this queue
     * @param resource
     *            the object to be added to the pool
     * @return <code>true</code> if the element was added to this pool, else
     *         <code>false</code>
     */
    boolean add(final R resource);

    /**
     * Removes specified <code>resource</code> from the pool while waiting to be
     * released if it was acquired.
     * <p>
     * If specified <code>resource</code> is currently in use, the remove
     * operation will block until that resource has been released.
     * 
     * @param resource
     *            the object to be removed from the pool
     * @return <code>true</code> if the element was removed from this pool, else
     *         <code>false</code>
     * @throws InterruptedException
     *             if interrupted while waiting <code>resource</code> to be
     *             released back to the pool if any
     */
    boolean remove(final R resource) throws InterruptedException;

    /**
     * Removes specified <code>resource</code> from the pool immediately.
     * 
     * @param resource
     *            the object to be removed from the pool
     * @return <code>true</code> if the element was removed from this pool, else
     *         <code>false</code>
     */
    boolean removeNow(final R resource);
}
