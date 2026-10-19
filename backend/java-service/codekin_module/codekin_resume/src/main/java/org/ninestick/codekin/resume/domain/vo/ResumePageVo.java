package org.ninestick.codekin.resume.domain.vo;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** 有界分页列表，额外取一行判断下一页，不为每条简历访问数据库。 @author ninestick */
@Getter @Builder
public class ResumePageVo {
    /** 当前页完整简历及临时照片链接，供列表直接渲染预览。 */
    private final List<ResumeDetailVo> items;
    private final int page;
    private final int pageSize;
    private final boolean hasMore;
}
