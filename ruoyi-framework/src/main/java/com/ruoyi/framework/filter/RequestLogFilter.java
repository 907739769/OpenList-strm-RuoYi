package com.ruoyi.framework.filter;

import com.ruoyi.common.utils.ThreadTraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Author Jack
 * @Date 2025/7/23 21:23
 * @Version 1.0.0
 */

@Component
@Slf4j
@Order(1) // 确保最先执行
public class RequestLogFilter implements Filter {

    private static final List<String> EXCLUDE_PARAMS = Arrays.asList("password");
    private static final int MAX_PARAM_LENGTH = 200;
    // 静态资源路径排除列表
    private static final List<String> EXCLUDE_PATHS = Arrays.asList(
            "/static/", "/ajax/libs/", "/fonts/", "/css/", "/js/", "/file/", "/html/", "/i18n/", "/img/",
            "/ruoyi/", "/images/", ".css", ".js", ".png", ".jpg", ".woff2", ".html", "/swagger-resources", "/webjars", ".ico",
            "/v2/api-docs", "/v3/api-docs"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        // 过滤静态资源请求
        if (isStaticResource(httpRequest.getRequestURI())) {
            if (!"GET".equalsIgnoreCase(httpRequest.getMethod())) {
                ((HttpServletResponse) response).sendError(405);
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        long startTime = System.nanoTime();

        // 包装请求以便重复读取body
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpRequest);

        try {
            // 初始化traceId
            initTraceId(httpRequest);

            // 记录请求信息
            logRequestInfo(wrappedRequest);

            chain.doFilter(wrappedRequest, response);

        } finally {
            // 记录耗时
            logElapsedTime(httpRequest, startTime);
            MDC.clear();
        }
    }

    private void initTraceId(HttpServletRequest request) {
        String traceId = Optional.ofNullable(request.getHeader("X-Trace-ID"))
                .orElseGet(ThreadTraceIdUtil::generateTraceId);
        MDC.put("traceId", traceId);
    }

    private void logRequestInfo(HttpServletRequest request) {
        try {
            Map<String, String> safeParams = getSafeParameters(request);

            log.info("Request => {} {} [Params: {}]",
                    request.getMethod(),
                    getRequestUrl(request),
                    formatParameters(safeParams));

        } catch (Exception e) {
            log.warn("记录请求日志出错", e);
        }
    }

    private String getRequestUrl(HttpServletRequest request) {
        String queryString = request.getQueryString();
        return request.getRequestURI() + (queryString != null ? "?" + queryString : "");
    }

    private Map<String, String> getSafeParameters(HttpServletRequest request) {
        Map<String, String> safeParams = new LinkedHashMap<>();

        // 获取URL参数
        request.getParameterMap().forEach((key, values) -> {
            if (!shouldExcludeParam(key) && values != null && values.length > 0) {
                String value = values[0];
                safeParams.put(key, truncateParam(value));
            } else if (shouldExcludeParam(key) && values != null && values.length > 0) {
                safeParams.put(key, "***");
            }
        });

        // 特殊处理文件上传请求
        if (isFileUpload(request)) {
            safeParams.put("_file_upload", getFileUploadDescription(request)); // 标记为文件请求
        }

        return safeParams;
    }

    private boolean shouldExcludeParam(String paramName) {
        return EXCLUDE_PARAMS.stream()
                .anyMatch(exclude -> paramName.toLowerCase().contains(exclude));
    }

    private String truncateParam(String value) {
        if (value.length() > MAX_PARAM_LENGTH) {
            return value.substring(0, MAX_PARAM_LENGTH) + "...[truncated]";
        }
        return value;
    }

    private String formatParameters(Map<String, String> params) {
        if (params.isEmpty()) {
            return "None";
        }
        return params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private void logElapsedTime(HttpServletRequest request, long startTime) {
        long elapsedTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        log.info("Response <= {} {} [Time: {}ms]",
                request.getMethod(),
                request.getRequestURI(),
                elapsedTime);
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }

    private boolean isStaticResource(String path) {
        return EXCLUDE_PATHS.stream()
                .anyMatch(exclude -> path.contains(exclude) || path.endsWith(exclude));
    }

    private boolean isFileUpload(HttpServletRequest request) {
        return request.getContentType() != null
                && request.getContentType().startsWith("multipart/form-data");
    }

    private String getFileUploadDescription(HttpServletRequest request) {
        try {
            if (request instanceof MultipartHttpServletRequest) {
                MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
                return multipartRequest.getFileMap().keySet().stream()
                        .collect(Collectors.joining(",", "[FILES: ", "]"));
            }
            return "[FILE_UPLOAD]";
        } catch (Exception e) {
            return "[FILE_PARSE_ERROR]";
        }

    }
}
