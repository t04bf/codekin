package org.ninestick.codekin.user.domain.dto.info;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 确认 builder 与请求体反序列化可以同时使用。
 *
 * @author ninestick
 */
class PasswordLoginDtoTest {

    @Test
    void jsonRequestStillUsesNoArgConstructor() {
        PasswordLoginDto request = JsonMapper.builder().build()
                .readValue("{\"identifier\":\"user@example.com\",\"password\":\"secret\"}",
                        PasswordLoginDto.class);

        assertEquals("user@example.com", request.getIdentifier());
        assertEquals("secret", request.getPassword());
    }
}
