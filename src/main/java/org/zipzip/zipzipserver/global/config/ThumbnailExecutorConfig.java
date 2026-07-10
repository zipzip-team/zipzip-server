package org.zipzip.zipzipserver.global.config;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 썸네일 생성은 무거운 디코딩 작업이라 인스턴스 자원에 맞춰 동시성을 캡한다(6.7 참조). */
@Configuration
@EnableAsync
public class ThumbnailExecutorConfig {

    @Bean(name = "thumbnailExecutor")
    public Executor thumbnailExecutor(
            @Value("${thumbnail.executor.core-pool-size:4}") int corePoolSize,
            @Value("${thumbnail.executor.max-pool-size:6}") int maxPoolSize,
            @Value("${thumbnail.executor.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("thumbnail-");
        executor.initialize();
        return executor;
    }
}
