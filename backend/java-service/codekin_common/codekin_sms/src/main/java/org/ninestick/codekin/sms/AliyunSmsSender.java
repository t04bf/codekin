package org.ninestick.codekin.sms;

import static org.ninestick.codekin.sms.SmsConstants.API_OK;
import static org.ninestick.codekin.sms.SmsConstants.BUSINESS_LIMIT_CONTROL;
import static org.ninestick.codekin.sms.SmsConstants.DEFINITE_REJECTION_CODES;
import static org.ninestick.codekin.sms.SmsConstants.FREQUENCY_FAIL;
import static org.ninestick.codekin.sms.SmsConstants.SIGNATURE_DOES_NOT_MATCH;

import com.aliyun.sdk.gateway.pop.exception.PopClientException;
import com.aliyun.sdk.gateway.pop.exception.PopServerException;
import com.aliyun.sdk.service.dypnsapi20170525.AsyncClient;
import com.aliyun.sdk.service.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.sdk.service.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.ninestick.codekin.model.common.BusinessException;
import org.springframework.util.StringUtils;

/**
 * 阿里云异步 SDK 发送适配器；验证码由业务服务生成和核验。
 *
 * @author ninestick
 */
@Slf4j
public class AliyunSmsSender implements SmsSender {
    /** Spring 容器管理生命周期的号码认证异步客户端。 */
    private final AsyncClient client;
    /** 阿里云发送请求要求提供的短信签名和模板。 */
    private final SmsProperties properties;

    public AliyunSmsSender(AsyncClient client, SmsProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void sendCode(String phoneNumber, String code, Duration validTime) {
        // 异步 SDK 2.0.0 在发起请求前校验签名和模板；验证码仍由业务服务生成。
        String templateParam = "{\"code\":\"" + code + "\",\"min\":\"" + validTime.toMinutes() + "\"}";
        SendSmsVerifyCodeRequest request = SendSmsVerifyCodeRequest.builder()
                .phoneNumber(phoneNumber)
                .signName(properties.getSignName())
                .templateCode(properties.getTemplateCode())
                .templateParam(templateParam)
                .build();
        SendSmsVerifyCodeResponse response;
        try {
            response = client.sendSmsVerifyCode(request).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("等待阿里云短信响应时线程被中断");
            throw new BusinessException(SmsCode.SERVICE_UNAVAILABLE);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof PopClientException providerException) {
                String providerCode = providerException.getErrCode();
                if (SIGNATURE_DOES_NOT_MATCH.equals(providerCode)) {
                    log.error("阿里云 AccessKey 请求签名校验失败 providerCode={}", providerCode);
                    throw new BusinessException(SmsCode.SEND_REJECTED);
                }
                if (FREQUENCY_FAIL.equals(providerCode) || BUSINESS_LIMIT_CONTROL.equals(providerCode)) {
                    log.warn("阿里云短信发送受限 providerCode={}", providerCode);
                    throw new BusinessException(SmsCode.SEND_TOO_FREQUENT);
                }
                if (StringUtils.hasText(providerCode) && DEFINITE_REJECTION_CODES.contains(providerCode)) {
                    log.warn("阿里云短信发送被拒绝 providerCode={}", providerCode);
                    throw new BusinessException(SmsCode.SEND_REJECTED);
                }
                log.error("阿里云短信发送状态不明 providerCode={}", providerCode);
            } else if (cause instanceof PopServerException providerException) {
                log.error("阿里云短信服务端异常 providerCode={}", providerException.getErrCode());
            } else {
                log.error("阿里云短信调用异常 exceptionType={}",
                        cause == null ? "null" : cause.getClass().getSimpleName());
            }
            throw new BusinessException(SmsCode.SERVICE_UNAVAILABLE);
        } catch (RuntimeException exception) {
            log.error("阿里云短信调用异常 exceptionType={}", exception.getClass().getSimpleName());
            throw new BusinessException(SmsCode.SERVICE_UNAVAILABLE);
        }
        if (response == null || response.getBody() == null) {
            log.error("阿里云短信响应为空");
            throw new BusinessException(SmsCode.SERVICE_UNAVAILABLE);
        }
        if (!Boolean.TRUE.equals(response.getBody().getSuccess()) || !API_OK.equals(response.getBody().getCode())) {
            String providerCode = response.getBody().getCode();
            log.warn("阿里云短信发送被拒绝 providerCode={}", providerCode);
            if (FREQUENCY_FAIL.equals(providerCode) || BUSINESS_LIMIT_CONTROL.equals(providerCode)) {
                throw new BusinessException(SmsCode.SEND_TOO_FREQUENT);
            }
            throw new BusinessException(SmsCode.SEND_REJECTED);
        }
        log.info("短信验证码发送请求已受理 requestId={}", response.getBody().getRequestId());
    }
}
