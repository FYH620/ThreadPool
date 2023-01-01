package com.fyh.threadpool.main;

import com.fyh.threadpool.util.ThreadPoolUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Data
@Slf4j
public class StretchableThreadPool {
    // 线程池列表（存的是当前线程的ID）
    private List<Long> workers;

    // 任务队列
    private Queue<Runnable> workQueue;

    // 每一个线程的状态（包含等待任务、正在忙碌、已经无效三种状态）
    private List<WorkerState> workersSign;

    // 该线程池的互斥锁（这把锁同时锁任务队列和线程池本身）
    private ReentrantLock lock;

    // 挂载到lock互斥锁上的等待室（线程会在这里等待被条件变量发信号唤醒）
    private Condition waitingRoomCondition;

    // 当前线程池的最小线程数，最大线程数和当前线程池中的线程总数
    private int minThreadCount;
    private int maxThreadCount;
    private int nowThreadCount;

    // 一个线程等三秒仍然没有任务就自杀
    private int maxWaitSeconds;

    // 线程池按照一次增加5个进行动态扩充
    private int addOnceThreadCount;

    // 线程名称递增ID号
    private int threadIncrementId = 1;


    /**
     * 初始化线程池
     *
     * @param minThreadCount:线程池最小线程数目
     * @param maxThreadCount:线程池最大线程数目
     */
    public void initThreadPool(int minThreadCount, int maxThreadCount, int maxWaitSeconds, int addOnceThreadCount) {
        // 1.初始化线程池伸缩量在[minThreadCount,maxThreadCount]之间，初始为0个线程
        // 等待maxWaitSeconds没任务就线程自杀，一次批量增加addOnceThreadCount个线程
        this.minThreadCount = minThreadCount;
        this.maxThreadCount = maxThreadCount;
        this.maxWaitSeconds = maxWaitSeconds;
        this.addOnceThreadCount = addOnceThreadCount;
        this.nowThreadCount = 0;

        // 2.初始化线程池列表、任务队列、线程池标志
        this.workers = new ArrayList<>(maxThreadCount);
        this.workQueue = new ArrayDeque<>(maxThreadCount);
        this.workersSign = new ArrayList<>(maxThreadCount);

        // 3.初始化锁与条件变量
        this.lock = new ReentrantLock();
        this.waitingRoomCondition = lock.newCondition();

        // 4.创建minThreadCount个线程开始运行任务
        for (int i = 0; i < minThreadCount; ++i) {
            // 增加处于等待状态的线程，这些线程阻塞等待任务来临，同时当前线程总数加一
            workersSign.add(WorkerState.WAITING);
            ++nowThreadCount;
            // 开启一个新线程并运行，同时加入线程池中让线程池管理
            Thread t = new Thread(this::workerFunction, String.valueOf(threadIncrementId++));
            workers.add(t.getId());
            t.start();
        }
        log.info("thread pool created, now has {} threads", nowThreadCount);
    }

    /**
     * 根据实际线程要执行的函数封装好任务并通知等待室中的线程来干活
     *
     * @param work:真正要执行的任务对象（需要重写Runnable接口中的run方法为自己想要执行的）
     */
    public void createNewWork(Runnable work) {
        // 1.把阻塞队列和线程池锁住，防止其他线程在这里对我们的队列进行多线程插入失败
        lock.lock();
        workQueue.add(work);
        log.info("new work added for function {}", work);

        // 2.判断线程池是否需要扩充（当只有一个可用的线程且有足够空间时扩充）
        if (ThreadPoolUtils.getWaitingStateThreadCount(this) <= 0 &&
                nowThreadCount + addOnceThreadCount <= maxThreadCount) {
            log.info("* thread pool extended. now has {} threads", nowThreadCount + addOnceThreadCount);
            for (int i = 0; i < addOnceThreadCount; i++) {
                // 设置新线程为等待状态同时递增线程数
                workersSign.add(WorkerState.WAITING);
                ++nowThreadCount;
                // 创建线程将ID存入workers列表中
                Thread t = new Thread(this::workerFunction, String.valueOf(threadIncrementId++));
                workers.add(t.getId());
                t.start();
            }
        }

        // 3.随机唤醒一个在等待室里面等待的线程处理当前任务就好（当前线程必须持有锁才能signal）
        waitingRoomCondition.signal();

        // 4.新增完毕互斥锁放心解锁
        lock.unlock();
    }

    /**
     * 线程池中每个线程真正在执行的方法
     */
    private void workerFunction() {
        while (true) {
            // 上来就先加锁，保证下面操作的原子性
            lock.lock();

            // 如果任务队列中有任务就要取出来执行
            if (!workQueue.isEmpty()) {
                // 1.从任务队列取出当前任务
                Runnable workToDo = workQueue.poll();
                int nowThreadIndex = ThreadPoolUtils.getIndexOfCurrentThread(this);

                // 2.设置当前线程为忙碌状态（开始处理任务）
                workersSign.set(nowThreadIndex, WorkerState.BUSY);

                // 3.当前线程解除所拿到的锁开始执行任务
                lock.unlock();

                // 4.执行任务
                log.info("thread {} work for function: {}}", Thread.currentThread().getName(), workToDo);
                workToDo.run();

                // 5.工作完成后重新加锁，设置为空闲状态后解锁continue去循环准备接收新任务
                lock.lock();
                workersSign.set(nowThreadIndex, WorkerState.WAITING);
                lock.unlock();
                continue;
            }

            try {
                // 条件等待超时
                if (!waitingRoomCondition.await(maxWaitSeconds, TimeUnit.SECONDS)) {
                    // 线程能自杀（满足可伸缩的范围）-
                    if (nowThreadCount > minThreadCount) {
                        // 结束线程（找到最后一个可用的线程放在当前要杀死的线程处），移除最后一个线程
                        int nowThreadIndex = ThreadPoolUtils.getIndexOfCurrentThread(this);
                        int lastThreadIndex = nowThreadCount - 1;
                        Collections.swap(workers, lastThreadIndex, nowThreadIndex);
                        Collections.swap(workersSign, lastThreadIndex, nowThreadIndex);
                        workers.remove(lastThreadIndex);
                        workersSign.remove(lastThreadIndex);
                        // 当前线程数减少1个
                        --nowThreadCount;
                        log.info("* thread {} end, left {} threads in pool", Thread.currentThread().getName(), nowThreadCount);
                        // 解锁彻底退出该循环，当前线程死亡
                        lock.unlock();
                        break;
                    }
                    // 线程不能自杀，已经不够杀了，要有一定最小范围
                    else {
                        // 解锁continue去接收任务
                        lock.unlock();
                        continue;
                    }
                }

                // 没超时，被条件变量唤醒（新任务来了）
                else {
                    log.info("thread {} called by cond obj", Thread.currentThread().getName());
                    // 唤醒后条件变量还锁给它，要解锁continue去接收任务
                    lock.unlock();
                    continue;
                }
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            }
        }
    }
}
