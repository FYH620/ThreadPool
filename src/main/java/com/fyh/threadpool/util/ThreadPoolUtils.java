package com.fyh.threadpool.util;

import com.fyh.threadpool.main.StretchableThreadPool;
import com.fyh.threadpool.main.WorkerState;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ThreadPoolUtils {

    /**
     * 获取当前处于等待状态（可以处理新任务）的线程数目
     *
     * @param pool:线程池对象
     * @return:线程数目
     */
    public static int getWaitingStateThreadCount(StretchableThreadPool pool) {
        int cnt = 0;
        for (WorkerState workerState : pool.getWorkersSign()) {
            if (workerState.equals(WorkerState.WAITING)) {
                ++cnt;
            }
        }
        return cnt;
    }

    /**
     * 获取当前线程在数组中的索引位置
     *
     * @param pool:线程池对象
     * @return:线程索引
     */
    public static int getIndexOfCurrentThread(StretchableThreadPool pool) {
        List<Long> workers = pool.getWorkers();
        for (int i = 0; i < workers.size(); i++) {
            if (workers.get(i).equals(Thread.currentThread().getId())) {
                return i;
            }
        }
        log.error("this thread no found in data array");
        throw new RuntimeException("this thread no found in data array");
    }
}
