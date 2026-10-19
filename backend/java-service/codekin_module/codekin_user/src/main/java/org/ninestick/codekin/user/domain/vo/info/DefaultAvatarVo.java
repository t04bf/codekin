package org.ninestick.codekin.user.domain.vo.info;

import lombok.*;

/**
 * 预置头像选项，稳定路径用于提交选择，签名链接仅用于当前展示。
 *
 * @author ninestick
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DefaultAvatarVo {
    /** 可读的头像名称。 */
    private String name;
    /** Nacos 配置的存储桶内对象路径。 */
    private String objectKey;
    /** 浏览器可访问的短期签名 URL，不持久化到用户资料。 */
    private String url;
}
