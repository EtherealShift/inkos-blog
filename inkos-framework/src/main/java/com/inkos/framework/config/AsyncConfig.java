package com.inkos.framework.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池。
 *
 * <p>刻意不用默认的 {@code SimpleAsyncTaskExecutor}（每次新建线程、无上限）。
 * 拒绝策略选择 CallerRuns：队列满时让调用线程自己执行，用「变慢」代替「丢任务」——
 * 对日志落库、通知发送这类场景，宁可慢也不能丢。
 */
@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig {

    public static final String ASYNC_EXECUTOR = "inkosAsyncExecutor";

    @Bean(name = ASYNC_EXECUTOR)
    public Executor inkosAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int cores = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(cores * 2);
        executor.setMaxPoolSize(cores * 4);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("inkos-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅停机：等待已提交任务执行完，避免关服时丢日志
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
