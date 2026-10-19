package org.ninestick.codekin.file.domain.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * 文件确认后的浏览器响应；不返回内部归属字段或对象路径。
 *
 * @author ninestick
 */
@Getter
@Builder
public class ReadyFileVo {
    /** 可供业务接口引用的文件 ID，使用字符串避免前端整数精度丢失。 */
    private final String fileId;
    /** 核验后的业务用途。 */
    private final String typeCode;
    /** 重编码后的实际字节数。 */
    private final long size;
    /** 正式文件实际 MIME。 */
    private final String contentType;
}
