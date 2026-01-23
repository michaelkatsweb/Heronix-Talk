package com.heronix.talk.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async configuration for handling concurrent operations.
 *
 * Provides thread pools for:
 * - General async operations
 * - WebSocket broadcasting
 * - Background tasks (sync, cleanup)
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    @Value("${heronix.async.core-pool-size:10}")
    private int corePoolSize;

    @Value("${heronix.async.max-pool-size:50}")
    private int maxPoolSize;

    @Value("${heronix.async.queue-capacity:500}")
    private int queueCapacity;

    @Value("${heronix.async.thread-name-prefix:heronix-async-}")
    private String threadNamePrefix;

    @Value("${heronix.websocket.broadcast-thread-pool-size:8}")
    private int broadcastPoolSize;

    /**
     * Main async task executor for general async operations.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("Task executor configured: core={}, max={}, queue={}",
                corePoolSize, maxPoolSize, queueCapacity);

        return executor;
    }

    /**
     * Dedicated executor for WebSocket broadcast operations.
     * Sized for handling rapid message delivery to 200+ users.
     */
    @Bean(name = "broadcastExecutor")
    public Executor broadcastExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(broadcastPoolSize);
        executor.setMaxPoolSize(broadcastPoolSize * 2);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("ws-broadcast-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();

        log.info("Broadcast executor configured: core={}, max={}, queue={}",
                broadcastPoolSize, broadcastPoolSize * 2, 1000);

        return executor;
    }

    /**
     * Executor for background sync and cleanup tasks.
     */
    @Bean(name = "backgroundExecutor")
    public Executor backgroundExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("background-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        log.info("Background executor configured: core=2, max=5, queue=100");

        return executor;
    }
}
