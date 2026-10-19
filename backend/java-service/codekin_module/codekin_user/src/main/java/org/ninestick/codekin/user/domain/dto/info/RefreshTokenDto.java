package org.ninestick.codekin.user.domain.dto.info;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 一次性刷新令牌请求，不输出令牌明文。
 *
 * @author ninestick
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenDto {
    /** 最近一次登录或刷新返回的不透明 Refresh Token。 */
    @NotBlank
    private String refreshToken;
}
