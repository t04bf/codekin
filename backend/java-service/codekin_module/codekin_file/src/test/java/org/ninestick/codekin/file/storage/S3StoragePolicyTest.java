package org.ninestick.codekin.file.storage;

import static org.junit.jupiter.api.Assertions.*;
import io.minio.MinioClient;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.ninestick.codekin.file.config.FileStorageProperties;
import org.ninestick.codekin.model.file.enums.UploadType;
import org.ninestick.codekin.utils.JsonUtils;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * SDK 本地生成真实 POST 签名，检查策略绑定，不连接或修改真实存储。
 *
 * @author ninestick
 */
class S3StoragePolicyTest {
    @ParameterizedTest
    @EnumSource(UploadType.class)
    void signedPolicyLocksObjectMimeExpiryAndSizeWithoutLeakingSecret(UploadType type) {
        var properties = new FileStorageProperties();
        properties.setBucket("codekin");
        properties.setRegion("us-east-1");
        properties.setPublicEndpoint(URI.create("http://127.0.0.1:8333"));
        var storage = new S3Storage();
        var client = MinioClient.builder().endpoint(properties.getPublicEndpoint().toString())
                .region("us-east-1").credentials("test-access", "test-secret-unused-for-network").build();
        ReflectionTestUtils.setField(storage, "properties", properties);
        ReflectionTestUtils.setField(storage, "publicStorageClient", client);
        ReflectionTestUtils.setField(storage, "initialized", true);
        String key = "uploads/" + type.getCode().toLowerCase(Locale.ROOT) + "/42/test.png";
        var fields = storage.createPostFields(key, "image/png", type.getMaxBytes(), Instant.now().plusSeconds(300));
        String policy = new String(Base64.getDecoder().decode(fields.get("policy")), java.nio.charset.StandardCharsets.UTF_8);
        Map<?, ?> json = JsonUtils.fromJson(policy, Map.class);
        assertNotNull(json.get("expiration"));
        String conditions = JsonUtils.toJson(json.get("conditions"));
        assertTrue(conditions.contains("content-length-range"));
        assertTrue(conditions.contains("5242880"));
        assertTrue(conditions.contains(key));
        assertTrue(conditions.contains("image/png"));
        assertNotNull(fields.get("x-amz-signature"));
        assertEquals(key, fields.get("key"));
        assertEquals("image/png", fields.get("Content-Type"));
        assertFalse(JsonUtils.toJson(fields).contains("test-secret-unused-for-network"));
    }
}
