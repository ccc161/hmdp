package com.hmdp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import java.util.concurrent.*;

@Configuration
public class ThreadPoolConfig {

    @Value("${spring.threads.virtual.enabled:false}")
    private boolean enableVirtualThreads;

    @Value("${spring.threads.core-pool-size:10}")
    private int corePoolSize;

    @Value("${spring.threads.max-pool-size:20}")
    private int maxPoolSize;

    @Value("${spring.threads.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Value("${spring.threads.queue-capacity:200}")
    private int queueCapacity;

    @Bean(destroyMethod = "shutdown")
    public ExecutorService threadPoolExecutor() {
        if (enableVirtualThreads) {
            return Executors.newVirtualThreadPerTaskExecutor();
        }

        return new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new CustomizableThreadFactory("app-thread-"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}