package org.ninestick.codekin.file.rpc;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import org.apache.dubbo.config.annotation.DubboService;
import org.ninestick.codekin.dubbo.api.file.IFileApi;
import org.ninestick.codekin.dubbo.api.file.FileRpcConstants;
import org.ninestick.codekin.file.service.IFileService;
import org.ninestick.codekin.model.common.*;
import org.ninestick.codekin.model.file.dto.ReadyFileDto;
import org.ninestick.codekin.model.file.dto.SignedFileUrlDto;
import org.ninestick.codekin.model.file.enums.UploadType;

/**
 * 内部文件核验接口，Dubbo token 及注册中心访问权限保护调用。
 *
 * @author ninestick
 */
@DubboService(token = "${CODEKIN_DUBBO_FILE_TOKEN}", group = FileRpcConstants.GROUP, version = FileRpcConstants.VERSION)
public class FileApiImpl implements IFileApi {
    /** 文件业务入口。 */
    @Resource private IFileService fileService;

    @Override
    public RpcResult<ReadyFileDto> getReadyFile(String fileId, Long ownerUserId, String typeCode) {
        if (fileId == null || !fileId.matches("[1-9][0-9]{0,18}") || ownerUserId == null || ownerUserId <= 0
                || UploadType.fromCode(typeCode) == null) {
            throw new BusinessException(CommonCode.INVALID_ARGUMENT, "文件 ID、所属用户或上传用途参数不正确");
        }
        Long id;
        try {
            id = Long.valueOf(fileId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(CommonCode.INVALID_ARGUMENT, "文件 ID 超出有效范围");
        }
        ReadyFileDto file = fileService.getReadyFile(id, ownerUserId, typeCode);
        return file == null ? RpcResult.failure(CommonCode.NOT_FOUND, "文件不存在、未确认或不属于指定用户")
                : RpcResult.success(file);
    }

    @Override
    public RpcResult<SignedFileUrlDto> getResumePhotoUrl(String fileId, Long ownerUserId) {
        if (fileId == null || !fileId.matches("[1-9][0-9]{0,18}") || ownerUserId == null || ownerUserId <= 0) {
            throw new BusinessException(CommonCode.INVALID_ARGUMENT);
        }
        Long id;
        try { id = Long.valueOf(fileId); }
        catch (NumberFormatException exception) { throw new BusinessException(CommonCode.INVALID_ARGUMENT); }
        return RpcResult.success(fileService.getResumePhotoUrl(id, ownerUserId));
    }

    @Override
    public RpcResult<Map<String, String>> getAvatarUrls(List<String> objectKeys) {
        return RpcResult.success(fileService.getAvatarUrls(objectKeys));
    }

    @Override
    public RpcResult<Map<String, SignedFileUrlDto>> getResumePhotoUrls(List<String> fileIds, Long ownerUserId) {
        if (fileIds == null || fileIds.size() > 100 || ownerUserId == null || ownerUserId <= 0
                || fileIds.stream().anyMatch(id -> id == null || !id.matches("[1-9][0-9]{0,18}"))) {
            throw new BusinessException(CommonCode.INVALID_ARGUMENT);
        }
        List<Long> ids;
        try { ids = fileIds.stream().map(Long::valueOf).distinct().toList(); }
        catch (NumberFormatException exception) { throw new BusinessException(CommonCode.INVALID_ARGUMENT); }
        return RpcResult.success(fileService.getResumePhotoUrls(ids, ownerUserId));
    }

    @Override
    public RpcResult<Map<String, String>> getDefaultAvatarUrls(List<String> objectKeys) {
        return RpcResult.success(fileService.getDefaultAvatarUrls(objectKeys));
    }
}
