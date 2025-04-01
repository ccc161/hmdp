package com.hmdp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.Executors;

import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {
    @Value("${spring.threads.virtual.enabled}")
    boolean enableVirtualThreads;

    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutorCustomizer() {
        return protocolHandler -> {
            if (enableVirtualThreads) {
                // 使用虚拟线程来处理每一个请求
                protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            }
        };
    }
}
