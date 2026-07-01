package com.meicrypt.identity.sso.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables {@link org.springframework.scheduling.annotation.Async @Async} for
 * the Back-Channel Logout dispatcher (Phase 8, Module 8.2).
 *
 * <p>A dedicated small pool keeps RP-notification traffic isolated from the
 * request-handling threads so a slow relying party never starves user-facing
 * endpoints.
 */
@Configuration
@EnableAsync
public class AsyncConfiguration {

    @Bean(name = "backchannelLogoutExecutor")
    public Executor backchannelLogoutExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("bcl-");
        executor.setKeepAliveSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }
}
