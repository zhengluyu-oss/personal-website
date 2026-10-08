package xyz.kuailemao.utils;

import com.alibaba.fastjson.JSON;
import xyz.kuailemao.domain.entity.Log;
import xyz.kuailemao.domain.response.ResponseResult;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/** Deliberately lossy audit projection. Never serialize application objects directly. */
public final class AuditDataProtection {
    private static final String OMITTED = "[REDACTED]";
    private static final int MAX_DEPTH = 4;
    private static final int MAX_ITEMS = 20;
    private static final Set<String> SAFE_FIELDS = Set.of(
            "id", "type", "typeId", "parentId", "pageNum", "pageSize", "status", "state",
            "orderNum", "isCheck", "isDeleted", "code");

    private AuditDataProtection() {}

    public static String request(Object[] args, String declaringClass, String method) {
        String operation = (declaringClass + "." + method).toLowerCase(Locale.ROOT);
        if (operation.matches(".*(user|auth|login|password|email|token|verify|register|secret).*")) {
            return OMITTED;
        }
        return JSON.toJSONString(project(args, 0, new IdentityHashMap<>()));
    }

    public static String response(Object result) {
        return result instanceof ResponseResult<?> response
                ? JSON.toJSONString(Collections.singletonMap("code", response.getCode())) : OMITTED;
    }

    private static Object project(Object value, int depth, IdentityHashMap<Object, Boolean> seen) {
        if (value == null) return null;
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte) return value;
        // Free text may contain credentials under any name. Do not rely on a key blacklist.
        if (value instanceof CharSequence || value instanceof Character || depth >= MAX_DEPTH) return OMITTED;
        if (seen.put(value, Boolean.TRUE) != null) return OMITTED;
        try {
            if (value.getClass().isArray()) {
                List<Object> result = new ArrayList<>();
                for (int i = 0; i < Math.min(Array.getLength(value), MAX_ITEMS); i++) {
                    result.add(project(Array.get(value, i), depth + 1, seen));
                }
                return result;
            }
            if (value instanceof Collection<?> collection) {
                List<Object> result = new ArrayList<>();
                int count = 0;
                for (Object item : collection) {
                    if (count++ >= MAX_ITEMS) break;
                    result.add(project(item, depth + 1, seen));
                }
                return result;
            }
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                int count = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (count++ >= MAX_ITEMS) break;
                    String key = entry.getKey() instanceof String text && SAFE_FIELDS.contains(text)
                            ? text : "omittedField" + count;
                    result.put(key, SAFE_FIELDS.contains(key)
                            ? project(entry.getValue(), depth + 1, seen) : OMITTED);
                }
                return result;
            }
            if (value.getClass().getPackageName().startsWith("xyz.kuailemao.domain.")) {
                Map<String, Object> result = new LinkedHashMap<>();
                int count = 0;
                for (Field field : value.getClass().getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    if (count++ >= MAX_ITEMS) break;
                    if (SAFE_FIELDS.contains(field.getName()) && field.trySetAccessible()) {
                        result.put(field.getName(), project(field.get(value), depth + 1, seen));
                    }
                }
                return result;
            }
            return OMITTED; // Requests, files, streams and arbitrary toString implementations.
        } catch (RuntimeException | IllegalAccessException ignored) {
            return OMITTED;
        }
    }

    public static void sanitizeQueuedLog(Log event) {
        // Legacy payloads are untrusted free text; omit instead of attempting to parse secrets.
        event.setReqParameter(OMITTED);
        event.setReturnParameter(OMITTED);
        if (event.getException() != null) event.setException("Operation failed (details omitted)");
        if (event.getReqAddress() != null) {
            event.setReqAddress(event.getReqAddress().split("[?#]", 2)[0]);
        }
    }
}
