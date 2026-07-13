package com.example.globalTimes_be.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AiSummaryAsyncConfig {

    @Bean(name = "aiSummaryExecutor")
    public ThreadPoolTaskExecutor aiSummaryExecutor(
            @Value("${ai.summary-async.core-pool-size:20}") int corePoolSize,
            @Value("${ai.summary-async.max-pool-size:20}") int maxPoolSize,
            @Value("${ai.summary-async.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ai-summary-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
