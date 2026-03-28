package com.hongdi.nexusforum.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author He Hongdi
 * @version 1.0.0
 * @ClassName ThreadPoolConfig.java
 * @Description Spring 线程池  Config
 * @createTime 2020/5/21 10:21
 */

@Configuration
@EnableScheduling
@EnableAsync
public class ThreadPoolConfig {
}
