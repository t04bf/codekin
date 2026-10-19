package org.ninestick.codekin.resume.service;

import org.ninestick.codekin.resume.domain.dto.ResumePdfDto;

/** 同步导出本人已保存简历，不创建任务或文件记录。 @author ninestick */
public interface IResumePdfService {
    byte[] exportPdf(Long resumeId, ResumePdfDto request);
}
