package com.cnhsk.learning.practice;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Scheduling is enabled once; tests disable the practice cron with its '-' property value. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class PracticeSchedulingConfiguration {
}
