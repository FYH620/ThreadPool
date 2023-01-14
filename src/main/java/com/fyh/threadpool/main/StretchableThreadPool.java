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
     * 一个线程等待多少毫秒仍然没有任务就自杀
     */
    private long maxWaitMilliseconds;

    /**
     * 线程名称递增ID号
     */
    private AtomicInteger threadIncrementId;


    /**
     * 线程锁用来锁住线程销毁，避免销毁的线程超出预期
     */
    private ReentrantLock lock;


    /**
     *
     * @param coreThreadCount 核心线程数量
     * @param maxThreadCount 最大线程数量
     * @param maxWaitMilliseconds 线程等待多长时间没有任务后死掉
     * @param workQueue 队列
     */
    public StretchableThreadPool(int coreThreadCount, int maxThreadCount, long maxWaitMilliseconds, BlockingQueue<Runnable> workQueue) {
        if (coreThreadCount > maxThreadCount) {
            throw new IllegalArgumentException("核心线程数量不能大于最大线程数量");
        }

        this.coreThreadCount = coreThreadCount;
        this.maxThreadCount = maxThreadCount;
        this.maxWaitMilliseconds = maxWaitMilliseconds;
        this.nowThreadCount = new AtomicInteger(0);
        this.threadIncrementId = new AtomicInteger(0);
        this.workQueue = workQueue;
        this.lock = new ReentrantLock();

        /*
         * 500毫秒判断一次是否需要扩容线程
         */
        this.startThreadsToExpandCapacity(500);

        // 扩容
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
    private  void workerFunction() {
        while (true) {
            try {
                //获取任务并等待，如果等待的时间超过设定的时间没有任务就需要判断是否销毁线程了
                Runnable workToDo = workQueue.poll(maxWaitMilliseconds, TimeUnit.MILLISECONDS);
                if (workToDo == null) {
                    //双重校验锁
                    if (nowThreadCount.get() > coreThreadCount) {
                        /*
                         * 尝试去加锁，如果加锁失败那就再次循环不去等待，防止同一时间内大量线程被销毁
                         * 原本想用synchronized来着，但判断不了是否被加锁过了
                         */
                        boolean bool = this.lock.tryLock();
                        if (bool) {
                            if (nowThreadCount.get() > coreThreadCount) {
                                log.info("* thread {} end, left {} threads in pool", Thread.currentThread().getName(), nowThreadCount.decrementAndGet());
                                this.lock.unlock();
                                break;
                            }
                            this.lock.unlock();
                        }
                    }
                    continue;
                }
                log.info("thread {} work for function: {}}", Thread.currentThread().getName(), workToDo);
                workToDo.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void createNewThread() {
        nowThreadCount.incrementAndGet();
        Thread thread = new Thread(this::workerFunction, String.valueOf(threadIncrementId.incrementAndGet()));
        thread.start();
    }


    /**
     * 我理解的应该是一个任务等待多长时间后没有被消费，在去扩容线程，不能直接扩容
     *
     * @param waitMilliseconds 等待毫秒
     */
    private void startThreadsToExpandCapacity(long waitMilliseconds) {
        new Thread(() -> {
            while (true) {
                try {
                    //进行等待
                    TimeUnit.MILLISECONDS.sleep(waitMilliseconds);

                    //等待后的任务容量
                    int afterSize = workQueue.size();

                    /*
                     * 当队列容量超过线程最大数量后在扩容
                     * 得任务多一些在扩容，要不很有可能任务很快就被消费完了
                     * 这个扩容条件还需要在优化...
                     */
                    if (afterSize > this.maxThreadCount) {
                        //判断是否能扩容线程，
                        if (this.nowThreadCount.get() + 1 <= this.maxThreadCount) {
                            this.createNewThread();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }


}
