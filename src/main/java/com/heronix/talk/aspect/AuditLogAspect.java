package com.heronix.talk.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Aspect for logging API requests and responses for audit purposes.
 * Captures request details, execution time, and outcomes.
 */
@Aspect
@Component
@Slf4j
public class AuditLogAspect {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Value("${heronix.audit.enabled:true}")
    private boolean auditEnabled;

    @Value("${heronix.audit.log-request-body:false}")
    private boolean logRequestBody;

    @Value("${heronix.audit.log-response:false}")
    private boolean logResponse;

    @Value("${heronix.audit.slow-request-threshold-ms:1000}")
    private long slowRequestThreshold;

    /**
     * Pointcut for all REST controller methods
     */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void restControllerMethods() {}

    /**
     * Pointcut for controller package
     */
    @Pointcut("execution(* com.heronix.talk.controller..*(..))")
    public void controllerPackage() {}

    /**
     * Around advice for auditing API requests
     */
    @Around("restControllerMethods() && controllerPackage()")
    public Object auditRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!auditEnabled) {
            return joinPoint.proceed();
        }

        long startTime = System.currentTimeMillis();
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);

        // Get request details
        HttpServletRequest request = getRequest();
        String method = request != null ? request.getMethod() : "UNKNOWN";
        String uri = request != null ? request.getRequestURI() : "UNKNOWN";
        String clientIp = getClientIp(request);
        String sessionToken = request != null ? maskToken(request.getHeader("X-Session-Token")) : null;
        String userAgent = request != null ? truncate(request.getHeader("User-Agent"), 50) : "none";

        // Get method details
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        // Log request start (include args only if configured)
        if (logRequestBody) {
            String args = formatArgs(joinPoint.getArgs());
            log.info("AUDIT-REQ | {} | {} {} | IP: {} | Session: {} | UA: {} | {}.{} | Args: {}",
                    timestamp, method, uri, clientIp, sessionToken, userAgent, className, methodName, args);
        } else {
            log.info("AUDIT-REQ | {} | {} {} | IP: {} | Session: {} | UA: {} | {}.{}",
                    timestamp, method, uri, clientIp, sessionToken, userAgent, className, methodName);
        }

        Object result = null;
        String status = "SUCCESS";
        String errorMessage = null;

        try {
            result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            status = "ERROR";
            errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            // Log response
            String responseInfo = logResponse && result != null ?
                    result.getClass().getSimpleName() : "[...]";

            if (status.equals("ERROR")) {
                log.warn("AUDIT-RES | {} | {} {} | Status: {} | Duration: {}ms | Error: {}",
                        timestamp, method, uri, status, duration, errorMessage);
            } else if (duration > slowRequestThreshold) {
                log.warn("AUDIT-RES | {} | {} {} | Status: {} | Duration: {}ms [SLOW] | Response: {}",
                        timestamp, method, uri, status, duration, responseInfo);
            } else {
                log.info("AUDIT-RES | {} | {} {} | Status: {} | Duration: {}ms | Response: {}",
                        timestamp, method, uri, status, duration, responseInfo);
            }
        }
    }

    /**
     * Get current HTTP request
     */
    private HttpServletRequest getRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get client IP address considering proxies
     */
    private String getClientIp(HttpServletRequest request) {
        if (request == null) return "UNKNOWN";

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * Mask session token for logging (show first/last 4 chars)
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 12) {
            return token != null ? "****" : "none";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    /**
     * Truncate string to max length
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return "none";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }

    /**
     * Format method arguments for logging (truncate long values)
     */
    private String formatArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }

        return Arrays.stream(args)
                .map(arg -> {
                    if (arg == null) return "null";
                    String str = arg.toString();
                    // Truncate long strings
                    if (str.length() > 100) {
                        return str.substring(0, 100) + "...";
                    }
                    // Mask password fields
                    if (str.toLowerCase().contains("password")) {
                        return "[MASKED]";
                    }
                    return str;
                })
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
