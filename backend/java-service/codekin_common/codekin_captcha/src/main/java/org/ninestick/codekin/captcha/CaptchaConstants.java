package org.ninestick.codekin.captcha;

/**
 * 验证码组件的缓存和输入边界常量。
 *
 * @author ninestick
 */
public final class CaptchaConstants {
    /** 图片生成次数按网关签名的客户端 IP 隔离。 */
    public static final String GENERATE_IP_PREFIX = "codekin:captcha:generate:ip:";
    /** 限制不可信输入中的验证码 ID 长度。 */
    public static final int MAX_ID_LENGTH = 80;
    /** 防止提交过大的轨迹列表占用校验资源。 */
    public static final int MAX_TRACK_POINTS = 1000;

    private CaptchaConstants() {
    }
}
