package com.ruoyi.framework.interceptor;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiRateLimiter {
    int maxRequests() default 100;
    int windowSeconds() default 60;
}
