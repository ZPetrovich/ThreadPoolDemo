package com.railsreactor.pool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.railsreactor.pool.impl.DefaultResourcePool;

/**
 * Some tests to check thread-safeness for {@link DefaultResourcePool}.
 * 
 * @author Michael Zayikin
 */
public class ResourcePoolTest {

    private static final int MAX_THREADS = 3;
    private static final int RESOURCES_NUM = 8;

    private ResourcePool<Object> resourcePool;
    private Object[] resources;

    private static final CyclicBarrier POOL_WORKER_BARRIER = new CyclicBarrier(MAX_THREADS);

    private List<PoolWorker<?>> poolWorkers;

    static final Object OK = new Object();
    private static final CountDownLatch STOP_LATCH = new CountDownLatch(MAX_THREADS);
    private CountDownLatch poolPopulatorLatch;
    private CountDownLatch poolEliminatorLatch;
    private ScheduledExecutorService poolPopulatorExecutorService;
    private ScheduledExecutorService poolEliminatorExecutorService;

    @SuppressWarnings("rawtypes")
    @Before
    public void setUp() throws InterruptedException {
        resourcePool = new DefaultResourcePool<Object>(RESOURCES_NUM);

        // create resources
        resources = new Object[RESOURCES_NUM];
        for (int i = 0; i < resources.length; i++) {
            resources[i] = new Object();
        }

        poolPopulatorExecutorService = Executors.newScheduledThreadPool(MAX_THREADS);
        poolEliminatorExecutorService = Executors.newScheduledThreadPool(MAX_THREADS);

        poolPopulatorLatch = new CountDownLatch(1);
        poolEliminatorLatch = new CountDownLatch(1);
        for (final Object resource : resources) {
            final PoolPopulator<Object> poolPopulator = new PoolPopulator<Object>(resourcePool, resource,
                    poolPopulatorLatch);
            poolPopulatorExecutorService.scheduleAtFixedRate(poolPopulator, 0, 5, TimeUnit.MILLISECONDS);

            final PoolEliminator poolEliminator = new PoolEliminator(resourcePool, resource, poolEliminatorLatch);
            poolEliminatorExecutorService.scheduleAtFixedRate(poolEliminator, 0, 2, TimeUnit.MILLISECONDS);
        }

        // create workers
        poolWorkers = new ArrayList<>();
        for (int i = 0; i < MAX_THREADS; i++) {
            poolWorkers.add(new PoolWorker<Object>(resourcePool, POOL_WORKER_BARRIER, STOP_LATCH));
        }

        // open pool
        resourcePool.open();

        // run populators
        poolPopulatorLatch.countDown();

        startEliminators();
    }

    @After
    public void tearDown() throws InterruptedException {
        if (resourcePool.isOpen()) {
            resourcePool.close();
        }
    }

    @Test
    public void testPoolWorkers() throws InterruptedException {
        // start workers
        final List<FutureTask<?>> futureTasks = startWorkers();

        // release 'remove' threads
        startEliminators();

        Thread.sleep(5000); // wait little bit system is working
        System.out.println("=======================================");

        stopAddRemove();

        // save initial resources
        final List<Object> initList = getListOfResources();

        stopWorkers();
        assertWorkers(futureTasks);

        // check resources are consistent
        checkResourcesConsistency(initList);
    }

    /**
     * Ensures thread will be blocked forever if it acquired resource and tries
     * to close the pool.
     */
    @Test
    public void testClose() throws InterruptedException, BrokenBarrierException {
        startAndStopAddRemove();

        final Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    final Object o = resourcePool.acquire();
                    Thread.sleep(3000);
                    resourcePool.release(o);
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            };
        };
        t.start();
        t.join();

        resourcePool.close();
        assertFalse("Pool should be closed", resourcePool.isOpen());
    }

    /**
     * Ensures thread will NOT be blocked forever if it acquired resource and
     * tries to close the pool.
     */
    @Test
    public void testCloseNow() throws InterruptedException, BrokenBarrierException {
        startAndStopAddRemove();

        final List<Object> initList = getListOfResources();

        resourcePool.acquire();
        resourcePool.closeNow();

        // check acquired resource wasn't returned to pool
        final List<Object> afterList = getListOfResources();
        assertEquals("Sizes should be equal", initList.size() - 1, afterList.size());

        assertFalse("ResourcePool should be closed", resourcePool.isOpen());
    }

    /**
     * Ensures {@link ResourcePool#closeNow()} working fine. Stacktrace with
     * {@link InterruptedException} is OK here.
     */
    @Test
    public void testCloseNowAfterClose() throws InterruptedException {
        startAndStopAddRemove();

        resourcePool.acquire();
        final Thread t1 = new Thread() {
            @Override
            public void run() {
                try {
                    resourcePool.close();
                } catch (final InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        final Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (final InterruptedException e) {
                    e.printStackTrace();
                }
                resourcePool.closeNow();
                t1.interrupt();
            };
        };
        t.start();
        t1.start();
        t.join();

        assertFalse("ResourcePool should be closed", resourcePool.isOpen());
    }

    @Test
    public void testDoubleClose() throws InterruptedException {
        startAndStopAddRemove();

        final List<Object> initList = getListOfResources();
        assertFalse("Pool shouldn't be empty before for this test case run", initList.isEmpty());

        class MyRunnable implements Runnable {
            private Object resource;

            @Override
            public void run() {
                try {
                    final Object o = resourcePool.acquire();
                    setResource(o);
                    Thread.sleep(3000);
                    resourcePool.closeNow();
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            }

            private void setResource(final Object o) {
                resource = o;
            };

            public Object getResource() {
                return resource;
            };
        }
        ;
        final MyRunnable r = new MyRunnable();
        final Thread t = new Thread(r);
        t.start();
        t.join();
        resourcePool.release(r.getResource());
        resourcePool.close();

        assertFalse("ResourcePool should be closed", resourcePool.isOpen());
    }

    private void startAndStopAddRemove() throws InterruptedException {
        startEliminators();
        Thread.sleep(1000);
        stopEliminators();
        Thread.sleep(100);
        stopPopulators();
        Thread.sleep(1000);
    }

    private void stopPopulators() {
        print("Trying to stop populators...");
        poolPopulatorExecutorService.shutdown();
        print("All populators are stopped!");
    }

    private void stopEliminators() {
        print("Trying to stop eliminators...");
        poolEliminatorExecutorService.shutdown();
        print("All eliminators are stopped!");
    }

    private void assertWorkers(final List<FutureTask<?>> futureTasks) throws InterruptedException {
        // check whether some problem occurred
        print("Check workers...");

        for (final FutureTask<?> futureTask : futureTasks) {
            try {
                // wait for a bit just to prevent forever blocking here
                final Object res = futureTask.get(1, TimeUnit.MILLISECONDS);
                Assert.assertEquals("Result of thread execution should be OK", OK, res);
            } catch (final ExecutionException e) {
                Assert.fail("Some worker thread thrown exception: " + e);
            } catch (final TimeoutException e) {
                Assert.fail("Can't retrieve caller result! " + e);
            }
        }
        print("workers are fine!");
    }

    private void stopWorkers() throws InterruptedException {
        print("Trying to stop workers...");
        // stop workers
        for (final PoolWorker<?> worker : poolWorkers) {
            worker.stop();
        }

        // wait all worker threads to stop
        STOP_LATCH.await();
        print("All workers stopped!");
    }

    private List<FutureTask<?>> startWorkers() {
        print("Trying to start workers...");
        final ExecutorService poolWorkerExecutorService = Executors.newFixedThreadPool(MAX_THREADS);
        final List<FutureTask<?>> futureTasks = new ArrayList<>();
        for (final Callable<?> worker : poolWorkers) {
            final FutureTask<?> futureTask = new FutureTask<>(worker);
            futureTasks.add(futureTask);
            poolWorkerExecutorService.submit(futureTask);

        }
        print("All workers started!");
        return futureTasks;
    }

    private void checkResourcesConsistency(final List<Object> initResources) throws InterruptedException {
        System.out.println("checkResourcesConsistency...");
        final List<Object> resList = getListOfResources();
        Assert.assertEquals("Resources count shouldn't be changed", RESOURCES_NUM, resList.size());
        for (final Object resource : initResources) {
            Assert.assertTrue("Resource Pool missed resource: " + resource, resList.contains(resource));
        }
    }

    private void startEliminators() {
        poolEliminatorLatch.countDown();
    }

    private void stopAddRemove() throws InterruptedException {
        // stop eliminators
        stopEliminators();

        print("populators are working...");
        Thread.sleep(2000); // wait populators do it job

        // stop populators
        stopPopulators();
    }

    @SuppressWarnings("rawtypes")
    private List<Object> getListOfResources() throws InterruptedException {
        Thread.sleep(100); // wait little bit
        final List<Object> initList = ((DefaultResourcePool) resourcePool).getResourcesSet();
        return initList;
    }

    static void print(final String text) {
        System.out.println(Thread.currentThread().getName() + "::" + text);
    }

}
