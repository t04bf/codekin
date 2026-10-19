package org.ninestick.codekin.resume.controller;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.ninestick.codekin.model.common.R;
import org.ninestick.codekin.resume.domain.dto.ResumeSaveDto;
import org.ninestick.codekin.resume.domain.dto.ResumeDeleteDto;
import org.ninestick.codekin.resume.domain.vo.*;
import org.ninestick.codekin.resume.service.IResumeService;
import org.ninestick.codekin.resume.service.IResumePdfService;
import org.ninestick.codekin.resume.domain.dto.ResumePdfDto;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 简历访问使用网关签发并由业务服务验签的当前用户身份。 @author ninestick */
@RestController
@RequestMapping("/api/users/me/resumes")
public class ResumeController {
    @Resource private IResumeService resumeService;
    @Resource private IResumePdfService resumePdfService;

    /** 模板目录；放在集合静态子路径避免与 ID 路由混淆。 */
    @GetMapping("/templates")
    public R<List<ResumeTemplateVo>> templates() { return R.success(resumeService.getTemplates()); }

    @GetMapping
    public R<ResumePageVo> list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.success(resumeService.getResumes(page, pageSize));
    }

    @GetMapping("/{resumeId}")
    public R<ResumeDetailVo> detail(@PathVariable Long resumeId) {
        return R.success(resumeService.getResume(resumeId));
    }

    /** 创建新简历并返回 ID；重复提交可能创建多份，后续修改使用 PUT。 */
    @PostMapping
    public R<ResumeDetailVo> create(@Valid @RequestBody ResumeSaveDto request) {
        return R.success(resumeService.createResume(request));
    }

    /** 并发冲突不会静默覆盖其他页面已保存的内容。 */
    @PutMapping("/{resumeId}")
    public R<ResumeDetailVo> save(@PathVariable Long resumeId, @Valid @RequestBody ResumeSaveDto request) {
        return R.success(resumeService.saveResume(resumeId, request));
    }

    /** 导出指定已保存版本，成功返回 PDF 字节；错误沿用业务信封。 */
    @PostMapping("/{resumeId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long resumeId, @Valid @RequestBody ResumePdfDto request) {
        byte[] pdf = resumePdfService.exportPdf(resumeId, request);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("resume-" + resumeId + ".pdf").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff").body(pdf);
    }

    /** 删除指定简历；版本通过查询参数传递，避免覆盖其他页面的修改。 */
    @DeleteMapping("/{resumeId}")
    public R<Void> delete(@PathVariable Long resumeId, @Valid @ModelAttribute ResumeDeleteDto request) {
        resumeService.deleteResume(resumeId, request);
        return R.success(null);
    }
}
