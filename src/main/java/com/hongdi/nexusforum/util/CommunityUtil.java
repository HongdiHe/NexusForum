package com.hongdi.nexusforum.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class CommunityUtil {

    private static final Logger logger = LoggerFactory.getLogger(CommunityUtil.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    // 生成随机字符串
    public static String generateUUID() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    // BCrypt加密（替代原MD5）
    public static String md5(String key) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        return PASSWORD_ENCODER.encode(key);
    }

    // BCrypt密码验证
    public static boolean matchPassword(String rawPassword, String encodedPassword) {
        if (StringUtils.isBlank(rawPassword) || StringUtils.isBlank(encodedPassword)) {
            return false;
        }
        return PASSWORD_ENCODER.matches(rawPassword, encodedPassword);
    }

    public static String getJSONString(int code, String msg, Map<String, Object> map) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("code", code);
        json.put("msg", msg);
        if (map != null) {
            json.putAll(map);
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(json);
        } catch (Exception e) {
            logger.error("JSON序列化失败", e);
            return "{}";
        }
    }

    public static String getJSONString(int code, String msg) {
        return getJSONString(code, msg, null);
    }

    public static String getJSONString(int code) {
        return getJSONString(code, null, null);
    }

    /**
     * Expose the shared ObjectMapper for other classes to use.
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
}
