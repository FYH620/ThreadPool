package com.fyh.pool;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;


@Slf4j
public class ThreadPool {
    // 线程状态：正在执行任务则为BUSY，等待任务为WAITING
    private enum WorkerState {
        WAITING,
        BUSY;
    }

    // 线程池中的线程与对应状态
    private Map<Thread, WorkerState> threadToState = new ConcurrentHashMap<>();

    // 任务队列
    private BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();

    // 当前线程池的线程总数
    private AtomicInteger nowThreadCount = new AtomicInteger(0);

    // 线程名称递增ID号
    private AtomicInteger threadIncrementId = new AtomicInteger(0);

    // 核心线程数和最大线程数
    private int coreThreadCount;
    private int maxThreadCount;

    // 一个线程等timeout timeUnit后仍然没有任务就自杀
    private int timeout;
    private TimeUnit timeUnit;

    // 线程池按照一次增加addOnceThreadCount个进行动态扩充
    private int addOnceThreadCount;


    /**
     * 初始化线程池
     */
    public ThreadPool(int coreThreadCount, int maxThreadCount,
                      int timeout, TimeUnit timeUnit, int addOnceThreadCount) {
        // 初始化线程池线程池线程数量在[coreThreadCount,maxThreadCount]之间
        // 一个线程等timeout timeUnit后仍然没有任务就自杀，一次批量增加addOnceThreadCount个线程
        this.coreThreadCount = coreThreadCount;
        this.maxThreadCount = maxThreadCount;
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        this.addOnceThreadCount = addOnceThreadCount;

        // 创建coreThreadCount个线程开始运行任务
        for (int i = 0; i < coreThreadCount; ++i) {
            // 增加处于等待状态的线程，这些线程阻塞等待任务来临，同时当前线程总数加一
            Thread t = new Thread(this::workerFunction, String.valueOf(threadIncrementId.incrementAndGet()));
            threadToState.put(t, WorkerState.WAITING);
            nowThreadCount.incrementAndGet();
            t.start();
        }
        log.info("thread pool created, now has {} threads", nowThreadCount.get());
    }

    /**
     * 根据实际线程要执行的函数封装好任务并通知等待室中的线程来干活
     *
     * @param work:真正要执行的任务对象（需要重写Runnable接口中的run方法为自己想要执行的）
     */
    public void submit(Runnable work) {
        // 1.把阻塞队列和线程池锁住，防止其他线程在这里对我们的队列进行多线程插入失败
        taskQueue.add(work);
        log.info("new work added for function {}", work);

        AtomicInteger waitingThreadCount = new AtomicInteger(0);
        for (WorkerState workerState : threadToState.values()) {
            if (workerState.equals(WorkerState.WAITING)) {
                waitingThreadCount.incrementAndGet();
            }
        }

        // 2.判断线程池是否需要扩充（当没有可用的线程且有足够空间时触发扩充）
        if (waitingThreadCount.get() <= 0 &&
                nowThreadCount.get() + addOnceThreadCount <= maxThreadCount) {

            for (int i = 0; i < addOnceThreadCount; i++) {
                // 设置新线程为等待状态同时递增线程数
                Thread t = new Thread(this::workerFunction, String.valueOf(threadIncrementId.incrementAndGet()));
                threadToState.put(t, WorkerState.WAITING);
                nowThreadCount.incrementAndGet();
                t.start();
            }

            log.info("* thread pool extended. now has {} threads",
                    nowThreadCount.get());
        }
    }

    /**
     * 线程池中每个线程真正在执行的方法
     */
    private void workerFunction() {
        while (true) {
            // 从任务队列按照一定超时时间阻塞等待取出任务
            Runnable workToDo = null;
            try {
                workToDo = taskQueue.poll(timeout, timeUnit);
            } catch (InterruptedException e) {
                // 调用shutdown方法时会在poll的过程中被产生中断异常，捕获后优雅退出
                nowThreadCount.decrementAndGet();
                break;
            }

            // 没取出任务，说明等待超时还没有取出任务
            if (workToDo == null) {
                // 线程能自杀则break
                if (nowThreadCount.get() > coreThreadCount) {
                    threadToState.remove(Thread.currentThread());
                    nowThreadCount.decrementAndGet();
                    log.info("* thread {} end, left {} threads in pool",
                            Thread.currentThread().getName(), nowThreadCount.get());
                    break;
                }
                // 线程不能自杀继续循环接受任务
                continue;
            }

            // 成功在规定时间内取出任务，设置当前线程为忙碌状态并开始处理任务
            log.info("thread {} work for function: {}}", Thread.currentThread().getName(), workToDo);
            threadToState.replace(Thread.currentThread(), WorkerState.BUSY);
            workToDo.run();
            log.info("function {} end", workToDo);

            // 工作完成后重新设置为空闲状态，准备在循环中接收新任务
            threadToState.replace(Thread.currentThread(), WorkerState.WAITING);
        }
    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        // 打断所有正在运行的线程，属性恢复为初始值，线程池关闭
        for (Thread thread : threadToState.keySet()) {
            thread.interrupt();
        }
        threadToState.clear();
        taskQueue.clear();
        threadIncrementId.set(0);
        log.info("* thread pool shutdown.");
    }

    public int getCoreThreadCount() {
        return coreThreadCount;
    }

    public void setCoreThreadCount(int coreThreadCount) {
        this.coreThreadCount = coreThreadCount;
    }

    public int getMaxThreadCount() {
        return maxThreadCount;
    }

    public void setMaxThreadCount(int maxThreadCount) {
        this.maxThreadCount = maxThreadCount;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public int getAddOnceThreadCount() {
        return addOnceThreadCount;
    }

    public void setAddOnceThreadCount(int addOnceThreadCount) {
        this.addOnceThreadCount = addOnceThreadCount;
    }
}
