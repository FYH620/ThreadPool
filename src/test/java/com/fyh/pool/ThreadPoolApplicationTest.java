package com.fyh.pool;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootTest
class ThreadPoolApplicationTest {
    @Slf4j
    private static final class ActualWork implements Runnable {
        @Override
        public void run() {
            // 当前线程睡上5s（模拟当前线程处理该任务5s）
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                log.error(e.getMessage());
            }
        }
    }

    @Test
    public void testStretchablePool() throws InterruptedException {
        // 初始化线程池，设置伸缩范围[5,15]，等待3s自杀，一次批量增加5个线程
        ThreadPool pool = new ThreadPool(5, 15,
                3, TimeUnit.SECONDS, 5);

        // 初始化后用submit传入一个实现了Runnable接口的任务对象，自动执行run方法
        for (int i = 0; i < 30; i++) {
            pool.submit(new ActualWork());
            Thread.sleep(100);
        }

        // 等待15s测试彻底结束
        Thread.sleep(15 * 1000);

        // 关闭线程池
        pool.shutdown();
        log.info("all work finished");
    }
}






