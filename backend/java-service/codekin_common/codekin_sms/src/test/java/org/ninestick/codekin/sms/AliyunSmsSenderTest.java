package org.ninestick.codekin.sms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aliyun.sdk.gateway.pop.exception.PopClientException;
import com.aliyun.sdk.service.dypnsapi20170525.AsyncClient;
import com.aliyun.sdk.service.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.sdk.service.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.sdk.service.dypnsapi20170525.models.SendSmsVerifyCodeResponseBody;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.ninestick.codekin.model.common.BusinessException;

/**
 * 验证异步 SDK 请求包含必填的签名和模板，验证码仍由业务服务提供。
 *
 * @author ninestick
 */
class AliyunSmsSenderTest {
    @Test
    void sendsBusinessCodeWithRequiredAliyunTemplate() {
        AsyncClient client = mock(AsyncClient.class);
        SendSmsVerifyCodeResponseBody body = SendSmsVerifyCodeResponseBody.builder()
                .success(true).code("OK").build();
        SendSmsVerifyCodeResponse response = SendSmsVerifyCodeResponse.create().toBuilder().body(body).build();
        when(client.sendSmsVerifyCode(any(SendSmsVerifyCodeRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        new AliyunSmsSender(client, smsProperties()).sendCode("13800138000", "123456", Duration.ofMinutes(5));

        ArgumentCaptor<SendSmsVerifyCodeRequest> captured =
                ArgumentCaptor.forClass(SendSmsVerifyCodeRequest.class);
        verify(client).sendSmsVerifyCode(captured.capture());
        assertEquals("13800138000", captured.getValue().getPhoneNumber());
        assertEquals("{\"code\":\"123456\",\"min\":\"5\"}", captured.getValue().getTemplateParam());
        assertEquals("test-sign", captured.getValue().getSignName());
        assertEquals("test-template", captured.getValue().getTemplateCode());
    }

    @Test
    void explicitProviderRejectionCanBeRevertedByBusinessLayer() {
        AsyncClient client = mock(AsyncClient.class);
        SendSmsVerifyCodeResponseBody body = SendSmsVerifyCodeResponseBody.builder()
                .success(false).code("INVALID_PARAMETERS").build();
        SendSmsVerifyCodeResponse response = SendSmsVerifyCodeResponse.create().toBuilder().body(body).build();
        when(client.sendSmsVerifyCode(any(SendSmsVerifyCodeRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new AliyunSmsSender(client, smsProperties())
                        .sendCode("13800138000", "123456", Duration.ofMinutes(5)));

        assertEquals(SmsCode.SEND_REJECTED, exception.getRecode());
    }

    @Test
    void invalidAccessKeySignatureIsAConfirmedRejection() {
        AsyncClient client = mock(AsyncClient.class);
        PopClientException providerError = new PopClientException();
        providerError.setErrCode(SmsConstants.SIGNATURE_DOES_NOT_MATCH);
        when(client.sendSmsVerifyCode(any(SendSmsVerifyCodeRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(providerError));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new AliyunSmsSender(client, smsProperties())
                        .sendCode("13800138000", "123456", Duration.ofMinutes(5)));

        assertEquals(SmsCode.SEND_REJECTED, exception.getRecode());
    }

    /** 为请求构造与生产环境相同形状的测试配置，不使用真实签名和模板。 */
    private SmsProperties smsProperties() {
        SmsProperties properties = new SmsProperties();
        properties.setRegion("cn-shenzhen");
        properties.setSignName("test-sign");
        properties.setTemplateCode("test-template");
        return properties;
    }
}
