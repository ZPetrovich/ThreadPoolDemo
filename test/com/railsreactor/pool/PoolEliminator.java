package com.railsreactor.pool;

import java.util.concurrent.CountDownLatch;

/**
 * @author Michael Zayikin
 */
final class PoolEliminator<R> implements Runnable {

    private final ResourcePool<R> resourcePool;
    private final R resource;
    private final CountDownLatch latch;

    public PoolEliminator(final ResourcePool<R> resourcePool, final R resource, final CountDownLatch latch) {
        this.resourcePool = resourcePool;
        this.resource = resource;
        this.latch = latch;
    }

    @Override
    public void run() {
        ResourcePoolTest.print("trying to remove...");
        try {
            latch.await();
            resourcePool.remove(resource);
            ResourcePoolTest.print("Resource removed!");
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
    }

}