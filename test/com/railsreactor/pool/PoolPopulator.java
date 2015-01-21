package com.railsreactor.pool;

import java.util.concurrent.CountDownLatch;

/**
 * @author Michael Zayikin
 */
public final class PoolPopulator<R> implements Runnable {

    private final ResourcePool<R> resourcePool;
    private final R resource;
    private final CountDownLatch latch;

    public PoolPopulator(final ResourcePool<R> resourcePool, final R resource, final CountDownLatch latch) {
        this.resourcePool = resourcePool;
        this.resource = resource;
        this.latch = latch;
    }

    @Override
    public void run() {
        ResourcePoolTest.print("PoolPopulator awaiting: " + latch.getCount());
        try {
            latch.await();
            ResourcePoolTest.print("PoolPopulator Go!");
            ResourcePoolTest.print("PoolPopulator trying to add...");
            final boolean res = resourcePool.add(resource);
            ResourcePoolTest.print("PoolPopulator resource: " + (res ? "added!" : "not added!"));
            ResourcePoolTest.print("PoolPopulator ended!");
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
    }
}