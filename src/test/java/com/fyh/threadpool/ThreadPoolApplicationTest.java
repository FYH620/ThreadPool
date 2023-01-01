package com.fyh.threadpool;

import com.fyh.threadpool.main.StretchableThreadPool;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
class ThreadPoolApplicationTest {

    @Test
    public void testStretchablePool() throws InterruptedException {
        // 1.测试线程池，创建可伸缩线程池对象
        StretchableThreadPool pool = new StretchableThreadPool();

        // 2.初始化线程池，设置伸缩范围[5,15]，等待3s自杀，一次批量增加5个线程
        pool.initThreadPool(5, 15, 3, 5);

        // 3.初始化后用createNewWork传入一个实现了Runnable接口的任务对象，自动执行run方法
        for (int i = 0; i < 30; i++) {
            pool.createNewWork(new ActualWork(i + 1));
            Thread.sleep(100);
        }

        // 4.等待15s测试彻底结束
        Thread.sleep(15 * 1000);
        log.info("all work finished");
    }

    @Test
    public void howToUse() {
        StretchableThreadPool pool = new StretchableThreadPool();
        pool.initThreadPool(5, 15, 3, 5);
        pool.createNewWork(new ActualWork(10));
    }
}


@Data
@Slf4j
@AllArgsConstructor
class ActualWork implements Runnable {
    private Integer workId;

    @Override
    public void run() {
        // 1.工作时打印当前任务的ID号
        log.info("work {} run in the thread pool", workId);

        // 2.当前线程睡上5s（模拟当前线程处理该任务5s）
        try {
            Thread.sleep(5 * 1000);
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        }

        // 3.当前任务结束
        log.info("work {} end", workId);
    }
}

