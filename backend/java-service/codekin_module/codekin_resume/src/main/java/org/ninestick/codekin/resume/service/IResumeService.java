package org.ninestick.codekin.resume.service;

import java.util.List;
import org.ninestick.codekin.resume.domain.dto.ResumeSaveDto;
import org.ninestick.codekin.resume.domain.dto.ResumeDeleteDto;
import org.ninestick.codekin.resume.domain.vo.*;

/** 当前用户私有简历的读取与保存边界。 @author ninestick */
public interface IResumeService {
    /** 获取已发布且符合当前前端组件协议的模板。 */
    List<ResumeTemplateVo> getTemplates();
    /** 分页获取本人未删除简历及预览数据，模板和证件照批量关联。 */
    ResumePageVo getResumes(int page, int pageSize);
    /** 获取单份本人未删除简历。 */
    ResumeDetailVo getResume(Long resumeId);
    /** 创建新简历，由服务端分配 ID、版本和时间。 */
    ResumeDetailVo createResume(ResumeSaveDto request);
    /** 以旧版本为条件完整替换，并支持最近一次更新的安全重试。 */
    ResumeDetailVo saveResume(Long resumeId, ResumeSaveDto request);
    /** 以读取到的版本为条件软删除本人简历。 */
    void deleteResume(Long resumeId, ResumeDeleteDto request);
}
