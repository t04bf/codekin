package org.ninestick.codekin.user.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ninestick.codekin.captcha.CaptchaCode;
import org.ninestick.codekin.captcha.CaptchaService;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.user.domain.dto.info.EmailSendDto;
import org.ninestick.codekin.user.domain.dto.info.SmsSendDto;
import org.ninestick.codekin.user.service.IUserEmailCodeService;
import org.ninestick.codekin.user.service.IUserSmsCodeService;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 防止直接访问登录发送接口绕过滑块，验证拒绝时不会产生厂商投递。
 *
 * @author ninestick
 */
class UserServiceImplCaptchaTest {
    /** 被测用户应用服务。 */
    private UserServiceImpl service;
    /** 公共一次性凭据入口。 */
    private CaptchaService captcha;
    /** 短信生成与投递业务。 */
    private IUserSmsCodeService sms;
    /** 邮件生成与投递业务。 */
    private IUserEmailCodeService email;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl();
        captcha = mock(CaptchaService.class);
        sms = mock(IUserSmsCodeService.class);
        email = mock(IUserEmailCodeService.class);
        ReflectionTestUtils.setField(service, "captchaService", captcha);
        ReflectionTestUtils.setField(service, "userSmsCodeService", sms);
        ReflectionTestUtils.setField(service, "userEmailCodeService", email);
    }

    @Test
    void missingOrInvalidCaptchaCannotSendSmsOrEmail() {
        doThrow(new BusinessException(CaptchaCode.REQUIRED)).when(captcha).consume(null);
        doThrow(new BusinessException(CaptchaCode.REQUIRED)).when(captcha).consume("invalid");
        assertThrows(BusinessException.class, () -> service.sendLoginCode(
                SmsSendDto.builder().phone("13800138000").build()));
        assertThrows(BusinessException.class, () -> service.sendEmailLoginCode(
                EmailSendDto.builder().email("test@example.com").captchaId("invalid").build()));
        verifyNoInteractions(sms, email);
    }

    @Test
    void successfulCaptchaIsConsumedBeforeSending() {
        service.sendLoginCode(SmsSendDto.builder().phone("13800138000").captchaId("sms-ticket").build());
        service.sendEmailLoginCode(EmailSendDto.builder().email("test@example.com").captchaId("email-ticket").build());
        var order = inOrder(captcha, sms, email);
        order.verify(captcha).consume("sms-ticket");
        order.verify(sms).sendLoginCode("13800138000");
        order.verify(captcha).consume("email-ticket");
        order.verify(email).sendLoginCode("test@example.com");
    }
}
