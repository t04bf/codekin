package org.ninestick.codekin.resume.service.impl;

import jakarta.annotation.Resource;
import java.util.*;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.resume.domain.dto.ResumePdfDto;
import org.ninestick.codekin.resume.domain.enums.ResumeCode;
import org.ninestick.codekin.resume.render.ResumePdfRenderer;
import org.ninestick.codekin.resume.service.*;
import org.ninestick.codekin.utils.JsonUtils;
import org.springframework.stereotype.Service;
import tools.jackson.databind.node.ObjectNode;

/** 读取权限沿用简历服务，固定导出快照；浏览器只使用受控组件与图片字节。 @author ninestick */
@Service
public class ResumePdfServiceImpl implements IResumePdfService {
    @Resource private IResumeService resumeService;
    @Resource private ResumePdfRenderer renderer;

    @Override
    public byte[] exportPdf(Long resumeId, ResumePdfDto request) {
        var detail = resumeService.getResume(resumeId);
        if (!detail.getVersion().equals(request.getExpectedVersion())) throw new BusinessException(ResumeCode.VERSION_CONFLICT);
        var snapshot = JsonUtils.fromJson(JsonUtils.toJson(detail), ObjectNode.class);
        var content = (ObjectNode) snapshot.get("content");
        Set<String> visible = new HashSet<>();
        detail.getLayout().path("menuSections").forEach(section -> {
            if (section.path("enabled").asBoolean()) visible.add(section.path("id").asString());
        });
        Set<String> required = new HashSet<>();
        var basic = (ObjectNode) content.get("basic");
        boolean photoVisible = visible.contains("basic") && detail.getLayout().path("basic").path("photoConfig").path("visible").asBoolean();
        if (photoVisible && (detail.getPhotoUrl() != null || !basic.path("photo").asString().isEmpty())) required.add("photo");
        if (visible.contains("certificates")) content.path("certificates").forEach(item -> required.add("certificate:" + item.path("id").asString()));
        if (!request.getImages().keySet().equals(required)) throw new BusinessException(ResumeCode.PDF_IMAGES_INVALID);
        int total = 0;
        for (var entry : request.getImages().entrySet()) {
            String value = entry.getValue();
            if (value == null || !value.matches("^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$")) throw new BusinessException(ResumeCode.PDF_IMAGES_INVALID);
            byte[] decoded;
            try { decoded = Base64.getDecoder().decode(value.substring(value.indexOf(',') + 1)); }
            catch (IllegalArgumentException invalid) { throw new BusinessException(ResumeCode.PDF_IMAGES_INVALID); }
            total += decoded.length;
            if (decoded.length == 0 || decoded.length > 5 * 1024 * 1024 || total > 15 * 1024 * 1024) throw new BusinessException(ResumeCode.PDF_IMAGES_INVALID);
        }
        if (required.contains("photo")) {
            basic.put("photo", request.getImages().get("photo"));
            snapshot.put("photoUrl", request.getImages().get("photo"));
        }
        if (visible.contains("certificates")) content.path("certificates").forEach(item ->
                ((ObjectNode) item).put("url", request.getImages().get("certificate:" + item.path("id").asString())));
        return renderer.render(snapshot);
    }
}
