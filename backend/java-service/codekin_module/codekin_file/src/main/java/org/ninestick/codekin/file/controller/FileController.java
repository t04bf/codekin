package org.ninestick.codekin.file.controller;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import org.ninestick.codekin.file.domain.dto.UploadSignatureDto;
import org.ninestick.codekin.file.domain.vo.*;
import org.ninestick.codekin.file.service.IFileService;
import org.ninestick.codekin.model.common.R;
import org.ninestick.codekin.model.file.enums.UploadType;
import org.ninestick.codekin.security.UserContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 统一文件接口，全部要求登录；存储文件正文由浏览器直传。
 *
 * @author ninestick
 */
@RestController
@RequestMapping("/api/files")
public class FileController {
    /** 文件应用服务。 */
    @Resource private IFileService fileService;

    /** 前端读取实际枚举限制，不能通过参数修改限制。 */
    @GetMapping("/upload-types")
    public R<List<UploadTypeVo>> uploadTypes() {
        UserContextHolder.requireUserId();
        return R.success(Arrays.stream(UploadType.values()).map(type -> UploadTypeVo.builder()
                .code(type.getCode()).maxBytes(type.getMaxBytes()).extensions(type.getExtensions())
                .contentTypes(type.getContentTypes()).maxWidth(type.getMaxWidth())
                .maxHeight(type.getMaxHeight()).maxPixels(type.getMaxPixels()).build()).toList());
    }

    /** 根据用途枚举 code 签发 POST 凭证。 */
    @PostMapping("/upload/signature")
    public R<UploadSignatureVo> signature(@Valid @RequestBody UploadSignatureDto request) {
        return R.success(fileService.createSignature(request, UserContextHolder.requireUserId()));
    }

    /** 确认只能操作本人文件，业务关联前必须完成此步骤。 */
    @PostMapping("/{fileId}/complete")
    public R<ReadyFileVo> complete(@PathVariable Long fileId) {
        var file = fileService.complete(fileId, UserContextHolder.requireUserId());
        return R.success(ReadyFileVo.builder().fileId(file.getFileId()).typeCode(file.getTypeCode())
                .size(file.getSize()).contentType(file.getContentType()).build());
    }
}

