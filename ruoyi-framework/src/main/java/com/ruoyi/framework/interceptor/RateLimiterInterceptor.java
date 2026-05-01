package com.ruoyi.framework.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimiterInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(RateLimiterInterceptor.class);
    private final Map<String, SlidingWindow> windows = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                            jakarta.servlet.http.HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) return true;
        HandlerMethod hm = (HandlerMethod) handler;
        ApiRateLimiter limiter = hm.getMethodAnnotation(ApiRateLimiter.class);
        if (limiter == null) {
            limiter = hm.getBeanType().getAnnotation(ApiRateLimiter.class);
        }
        if (limiter == null) return true;

        final int maxRequests = limiter.maxRequests();
        final int windowSeconds = limiter.windowSeconds();
        String key = request.getRemoteAddr() + ":" + hm.getMethod().getName();
        SlidingWindow sw = windows.computeIfAbsent(key, k -> new SlidingWindow(maxRequests, windowSeconds));
        if (!sw.tryAcquire()) {
            log.warn("[RateLimit] {} blocked", key);
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":429,\"msg\":\"请求过于频繁，请稍后再试\"}");
            return false;
        }
        return true;
    }

    private static class SlidingWindow {
        private final int maxRequests;
        private final long windowMs;
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();

        SlidingWindow(int maxRequests, int windowSeconds) {
            this.maxRequests = maxRequests;
            this.windowMs = windowSeconds * 1000L;
        }

        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - windowStart >= windowMs) {
                windowStart = now;
                count.set(0);
            }
            return count.incrementAndGet() <= maxRequests;
        }
    }
}
