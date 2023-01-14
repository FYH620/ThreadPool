package com.fyh.threadpool;

import com.fyh.threadpool.main.StretchableThreadPool;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.LinkedBlockingDeque;

@Slf4j
@SpringBootTest
class ThreadPoolApplicationTest {

    @Test
    public void testStretchablePool() throws InterruptedException {
        StretchableThreadPool pool = new StretchableThreadPool(5, 10,
                3000, new LinkedBlockingDeque<>());

        for (int i = 0; i < 30; i++) {
            pool.createNewWork(new ActualWork(i + 1));
            Thread.sleep(100);
        }

        Thread.sleep(30 * 1000);
        log.info("all work finished");
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

