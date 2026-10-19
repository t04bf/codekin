package org.ninestick.codekin.user.controller;

import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.ninestick.codekin.captcha.CaptchaService;
import org.ninestick.codekin.captcha.domain.dto.CaptchaCheckDto;
import org.ninestick.codekin.captcha.domain.vo.CaptchaTicketVo;
import org.ninestick.codekin.model.common.R;
import org.ninestick.codekin.security.UserContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户登录验证码发送前的滑块接口，复用公共组件的图片生成与轨迹核验。
 *
 * @author ninestick
 */
@RestController
@RequestMapping("/api/auth/captcha/slider")
public class UserLoginCaptchaController {
    /** 服务端验证及凭据管理入口。 */
    @Resource
    private CaptchaService captchaService;

    /** 创建随机滑块图片，正确答案仅保存在后端 Redis。 */
    @PostMapping("/generate")
    public R<ImageCaptchaVO> generate() {
        return R.success(captchaService.generate(UserContextHolder.requireClientIp()));
    }

    /** 核验 SDK 提交的轨迹，返回一次性通过凭据。 */
    @PostMapping("/check")
    public R<CaptchaTicketVo> check(@Valid @RequestBody CaptchaCheckDto request) {
        return R.success(captchaService.verify(request));
    }
}
