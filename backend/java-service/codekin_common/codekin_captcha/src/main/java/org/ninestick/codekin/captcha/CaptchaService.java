package org.ninestick.codekin.captcha;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.spring.plugins.secondary.SecondaryVerificationApplication;
import lombok.extern.slf4j.Slf4j;
import org.ninestick.codekin.captcha.domain.dto.CaptchaCheckDto;
import org.ninestick.codekin.captcha.domain.vo.CaptchaTicketVo;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.redis.RedisService;
import org.springframework.util.StringUtils;

/**
 * 服务端滑块生成、轨迹核验与一次性凭据消费的公共基础设施入口。
 *
 * @author ninestick
 */
@Slf4j
public class CaptchaService {
    /** 天爱组件负责图片生成、答案存储和轨迹校验。 */
    private final SecondaryVerificationApplication application;
    /** 生成额度统一使用已有 Redis 工具。 */
    private final RedisService redisService;
    /** Nacos 下发的生成窗口与额度。 */
    private final CaptchaProperties properties;

    public CaptchaService(ImageCaptchaApplication application, RedisService redisService,
                          CaptchaProperties properties) {
        if (!(application instanceof SecondaryVerificationApplication secondary)) {
            throw new IllegalStateException("codekin_captcha 必须配置 captcha.secondary.enabled=true");
        }
        if (properties.getGenerateLimit() < 1 || properties.getGenerateWindow() == null
                || properties.getGenerateWindow().toSeconds() < 1) {
            throw new IllegalArgumentException("验证码生成额度必须为正数，窗口至少为一秒");
        }
        this.application = secondary;
        this.redisService = redisService;
        this.properties = properties;
    }

    /** 网关已签名的客户端 IP 用于限制图片生成成本，不从任意转发头读取。 */
    public ImageCaptchaVO generate(String clientIp) {
        if (!redisService.incrementWithinLimit(CaptchaConstants.GENERATE_IP_PREFIX + clientIp,
                properties.getGenerateLimit(), properties.getGenerateWindow())) {
            throw new BusinessException(CaptchaCode.TOO_FREQUENT);
        }
        ApiResponse<ImageCaptchaVO> response = application.generateCaptcha(CaptchaTypeConstant.SLIDER);
        if (!response.isSuccess() || response.getData() == null) {
            log.error("滑块图片生成失败 providerCode={}", response.getCode());
            throw new BusinessException(CaptchaCode.UNAVAILABLE);
        }
        return response.getData();
    }

    /** 失败或成功都会消费图片状态；重新尝试必须获取新图片。 */
    public CaptchaTicketVo verify(CaptchaCheckDto request) {
        var track = request.getData();
        if (track == null || track.getTrackList() == null || track.getTrackList().size() < 2
                || track.getTrackList().size() > CaptchaConstants.MAX_TRACK_POINTS
                || track.getStartTime() == null || track.getStopTime() == null
                || track.getStopTime() <= track.getStartTime()
                || track.getBgImageWidth() == null || track.getBgImageWidth() <= 0
                || track.getBgImageHeight() == null || track.getBgImageHeight() <= 0
                || track.getTemplateImageWidth() == null || track.getTemplateImageWidth() <= 0
                || track.getTemplateImageHeight() == null || track.getTemplateImageHeight() <= 0
                || track.getTrackList().stream().anyMatch(point -> point == null
                        || point.getX() == null || point.getY() == null || point.getT() == null
                        || !Float.isFinite(point.getX()) || !Float.isFinite(point.getY())
                        || !Float.isFinite(point.getT()))) {
            throw new BusinessException(CaptchaCode.INVALID);
        }
        ApiResponse<?> response = application.matching(request.getId(), track);
        if (!response.isSuccess()) {
            throw new BusinessException(CaptchaCode.INVALID);
        }
        // matching 成功时组件已写入短时二次验证记录，此处只返回对应 ID。
        return CaptchaTicketVo.builder().captchaId(request.getId()).build();
    }

    /** 业务发送前必须调用；Redis 原子取出并删除，拒绝缺失、过期和已消费的凭据。 */
    public void consume(String captchaId) {
        if (!StringUtils.hasText(captchaId) || captchaId.length() > CaptchaConstants.MAX_ID_LENGTH
                || !application.secondaryVerification(captchaId)) {
            throw new BusinessException(CaptchaCode.REQUIRED);
        }
    }
}
