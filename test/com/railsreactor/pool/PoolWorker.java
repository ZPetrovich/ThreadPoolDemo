package com.railsreactor.pool;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

/**
 * @author Michael Zayikin
 */
public final class PoolWorker<R> implements Callable<Object> {

    private final ResourcePool<R> resourcePool;
    private final CyclicBarrier barrier;
    private volatile boolean isStopped;
    private final CountDownLatch stopLatch;

    public PoolWorker(final ResourcePool<R> resourcePool, final CyclicBarrier barrier, final CountDownLatch stopLatch) {
        this.resourcePool = resourcePool;
        this.barrier = barrier;
        this.stopLatch = stopLatch;
    }

    @Override
    public Object call() throws Exception {
        ResourcePoolTest.print("PoolWorker awaiting: " + barrier.getNumberWaiting());
        barrier.await();
        ResourcePoolTest.print("PoolWorker Go!");
        try {
            while (!isStopped()) {
                final R resource = resourcePool.acquire();
                if (resource == null) {
                    throw new RuntimeException("Acquired resource is null!");
                }
                Thread.sleep(4);
                resourcePool.release(resource);
            }
        } catch (final Exception e) {
            e.printStackTrace();
        } finally {
            stopLatch.countDown();
        }
        ResourcePoolTest.print("PoolWorker Finished!");
        return ResourcePoolTest.OK;
    }

    public boolean isStopped() {
        return isStopped;
    }

    public void stop() {
        this.isStopped = true;
    }
}