package com.fyh.threadpool.main;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class StretchableThreadPool {
    /**
     * 堵塞任务队列
     */
    private BlockingQueue<Runnable> workQueue;

    /**
     * 一个线程等待多少毫秒仍然没有任务就自杀
     */
    private long maxWaitMilliseconds;

    /**
     * 线程核心数
     */
    private int coreThreadCount;

    /**
     * 最大线程数
     */
    private int maxThreadCount;


    /**
     * 当前线程数量
     */
    private AtomicInteger nowThreadCount;

    /**
     * 线程名称递增ID号
     */
    private AtomicInteger threadIncrementThreadName;

    /**
     * 线程锁用来锁住线程销毁，避免销毁的线程超出预期
     */
    private ReentrantLock lock;

    /**
     * @param coreThreadCount     核心线程数量
     * @param maxThreadCount      最大线程数量
     * @param maxWaitMilliseconds 线程等待多长时间没有任务后自杀
     * @param workQueue           阻塞队列
     */
    public StretchableThreadPool(int coreThreadCount, int maxThreadCount, long maxWaitMilliseconds, BlockingQueue<Runnable> workQueue) {
        if (coreThreadCount > maxThreadCount) {
            log.error("核心线程数量不能大于最大线程数量");
        }

        // 初始化线程池参数
        this.coreThreadCount = coreThreadCount;
        this.maxThreadCount = maxThreadCount;
        this.maxWaitMilliseconds = maxWaitMilliseconds;
        this.workQueue = workQueue;

        // 初始化锁和线程池中的记录变量
        this.nowThreadCount = new AtomicInteger(0);
        this.threadIncrementThreadName = new AtomicInteger(0);
        this.lock = new ReentrantLock();

        // 500毫秒判断一次是否需要扩容线程（单独开一个监控线程用于监控扩容条件）
        this.startThreadsToExpandCapacity(500);

        // 创建核心线程数量的线程用于执行真正要执行的任务
        for (int i = 0; i < coreThreadCount; ++i) {
            this.createNewThread();
        }
        log.info("thread pool created, now has {} threads", nowThreadCount.get());
    }


    /**
     * @param work:真正要执行的任务对象（需要重写Runnable接口中的run方法为自己想要执行的）
     */
    public void createNewWork(Runnable work) {
        workQueue.add(work);
        log.info("new work added for function {}", work);
    }


    /**
     * 线程池中每个线程真正在执行的方法
     */
    private void workerFunction() {
        while (true) {
            try {
                // 尝试获取任务并等待，如果等待的时间超过设定的时间没有任务就需要判断是否销毁线程了
                Runnable workToDo = workQueue.poll(maxWaitMilliseconds, TimeUnit.MILLISECONDS);

                // 等待超时的情况（没取到任务）
                if (workToDo == null) {

                    // 双重校验自杀条件，确保安全自杀，线程池线程数量不会小于核心线程数
                    if (nowThreadCount.get() > coreThreadCount) {
                        // 尝试去加锁，如果加锁失败那就再次循环不去等待，防止同一时间内大量线程被销毁
                        boolean lockedSuccess = lock.tryLock();

                        // 加锁成功，再次判断是否满足自杀条件
                        if (lockedSuccess) {

                            // 满足自杀条件，线程自杀，解锁
                            if (nowThreadCount.get() > coreThreadCount) {
                                log.info("* thread {} end, left {} threads in pool", Thread.currentThread().getName(), nowThreadCount.decrementAndGet());
                                lock.unlock();
                                break;
                            }

                            // 不满足自杀条件就解锁继续循环
                            lock.unlock();
                        }
                    }
                    continue;
                }

                // 等待没有超时（取到了任务就开始执行）
                log.info("thread {} work for function: {}}", Thread.currentThread().getName(), workToDo);
                workToDo.run();

            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
    }

    private void createNewThread() {
        nowThreadCount.incrementAndGet();
        Thread t = new Thread(this::workerFunction, String.valueOf(threadIncrementThreadName.incrementAndGet()));
        t.start();
    }

    // 中控线程
    /**
     * 当一个任务等待waitMilliseconds时间后没有被消费且等待后任务队列容量超过线程池最大数量后再扩容
     *
     * @param waitMilliseconds 等待毫秒数
     */
    private void startThreadsToExpandCapacity(long waitMilliseconds) {
        new Thread(() -> {
            while (true) {
                try {
                    // 当前线程先等一等看看任务会不会被迅速消费
                    TimeUnit.MILLISECONDS.sleep(waitMilliseconds);
                    // 获取等待后的任务容量
                    int afterSize = workQueue.size();
                    // 如果可以扩容线程池则扩容
                    if (afterSize > this.maxThreadCount) {
                        if (this.nowThreadCount.get() + 1 <= this.maxThreadCount) {
                            this.createNewThread();
                        }
                    }
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            }
        }).start();
    }
}
