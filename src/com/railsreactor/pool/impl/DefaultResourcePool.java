package com.railsreactor.pool.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.railsreactor.pool.ResourcePool;

/**
 * Implementation for {@link ResourcePool} based on {@link BlockingQueue}.
 * 
 * <p>
 * <b>Note</b>, this implementation support only bounded resource pools.
 * 
 * @param <R>
 *            the generic resource class
 * @author Michael Zayikin
 */
public class DefaultResourcePool<R> implements ResourcePool<R> {

    /** Underlined space for resources. */
    private final BlockingQueue<R> resources;

    /**
     * Flag indicating whether pool is opened or not. Pool considered closed by
     * default so this flag is <code>false</code>.
     */
    private volatile boolean isOpenned;

    /** Latch need to avoid resource acquisition before it will be opened. */
    private CountDownLatch openPoolLatch = new CountDownLatch(1);

    /**
     * Latch that waits for zero countDowns. Needed to avoid comparison with
     * <code>null</code> in some places.
     */
    private static final CountDownLatch ZERO_LATCH = new CountDownLatch(0);

    /** Latch uses to release acquired resources before pool closing. */
    private CountDownLatch closePoolLatch = ZERO_LATCH;

    /** Lock to avoid break of open/close invariants. */
    private final ReentrantLock openCloseLock = new ReentrantLock();

    /** Lock to avoid break of acquire/release invariants. */
    private final Lock acquireReleaseLock = new ReentrantLock();

    /** Map of acquired resources. */
    private final Map<R, CountDownLatch> acquiredResources = new ConcurrentHashMap<>();

    /**
     * Creates pool with specified <code>bound</code>.
     * 
     * @param bound
     *            the length of the pool
     * @throws IllegalAccessError
     *             if <code>bound</code> is less or equal to zero
     */
    public DefaultResourcePool(final int bound) {
        // will use LinkedQueue but not ArrayQueue since according to
        // specification this pool mainly will be uses with acquire/release but
        // not add/remove but acquiring means basically remove resource from the
        // pool so LinkedQueue is more preferable here
        resources = new LinkedBlockingQueue<R>(bound);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void open() {
        openCloseLock.lock();
        try {
            openPoolLatch.countDown();
            closePoolLatch = ZERO_LATCH;
            isOpenned = true;
        } finally {
            // unlock in finally block to ensure nothing will be locked not
            // matter exception will be raised or not
            openCloseLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isOpen() {
        return isOpenned;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws InterruptedException {
        openCloseLock.lock();
        try {
            // create latch and wait for all acquired resources be released
            final int size = acquiredResources.size();
            closePoolLatch = new CountDownLatch(size);
            closePoolLatch.await();

            // release all waiting threads
            openPoolLatch.countDown();

            // recreate latch since we can't just reset it
            openPoolLatch = new CountDownLatch(1);

            isOpenned = false;
        } finally {
            openCloseLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void closeNow() {
        boolean openCloseLockLocked = false;
        if (closePoolLatch.getCount() == 0) {
            openCloseLock.lock();
            openCloseLockLocked = true;
        }
        try {
            // release all waiting threads
            openPoolLatch.countDown();

            // recreate latch since we can't just reset it
            openPoolLatch = new CountDownLatch(1);

            isOpenned = false;
        } finally {
            if (openCloseLockLocked) {
                openCloseLock.unlock();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public R acquire() throws InterruptedException {
        // wait until pool will be opened
        openPoolLatch.await();
        acquireReleaseLock.lock();
        try {
            final R peek = resources.take();
            acquiredResources.put(peek, new CountDownLatch(1));
            return peek;
        } finally {
            acquireReleaseLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public R acquire(final long timeout, final TimeUnit timeUnit) throws InterruptedException {
        // wait until pool will be opened
        openPoolLatch.await();

        // use tryLock here since this method should guarantee return some value
        // after specified period of time
        if (acquireReleaseLock.tryLock(timeout, timeUnit)) {
            try {
                final R poll = resources.poll(timeout, timeUnit);
                acquiredResources.put(poll, new CountDownLatch(1));
                return poll;
            } finally {
                acquireReleaseLock.unlock();
            }
        } else {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void release(final R resource) {
        acquireReleaseLock.lock();
        try {
            resources.offer(resource);

            final CountDownLatch latch = acquiredResources.remove(resource);
            if (latch != null) {
                latch.countDown();
            }

            closePoolLatch.countDown();
        } finally {
            acquireReleaseLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean add(final R resource) {
        return resources.offer(resource);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean remove(final R resource) throws InterruptedException {
        acquireReleaseLock.lock();
        try {
            final CountDownLatch latch = acquiredResources.get(resource);
            if (latch != null) {
                // unlock to give a chance to threads for releasing this
                // resource
                acquireReleaseLock.unlock();

                // wait till released
                latch.await();
            }
            return resources.remove(resource);
        } finally {
            acquireReleaseLock.unlock();// in case of any exception occurred
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeNow(final R resource) {
        return resources.remove(resource);
    }

    /**
     * Returns current the list of resources in the pool. It is not a part of
     * {@link ResourcePool} interface.
     * 
     * @return the list of resources
     */
    @SuppressWarnings("unchecked")
    public List<R> getResourcesSet() {
        return (List<R>) Arrays.asList(resources.toArray());
    }
}
