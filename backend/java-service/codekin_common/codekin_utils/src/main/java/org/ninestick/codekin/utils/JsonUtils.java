package org.ninestick.codekin.utils;

import tools.jackson.databind.json.JsonMapper;

/**
 * 基于 Jackson 3 的公共 JSON 转换入口。
 *
 * @author ninestick
 */
public final class JsonUtils {

    private static final JsonMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    private JsonUtils() {
    }

    /** 将对象序列化为 JSON，序列化错误向调用方传播。 */
    public static String toJson(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    /** 将 JSON 反序列化为指定类型。 */
    public static <T> T fromJson(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }
}
