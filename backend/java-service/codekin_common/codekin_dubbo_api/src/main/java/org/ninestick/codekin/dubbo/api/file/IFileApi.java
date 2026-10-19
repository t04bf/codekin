package org.ninestick.codekin.dubbo.api.file;

import java.util.List;
import java.util.Map;
import org.ninestick.codekin.model.common.RpcResult;
import org.ninestick.codekin.model.file.dto.ReadyFileDto;
import org.ninestick.codekin.model.file.dto.SignedFileUrlDto;

/**
 * 业务服务通过 Dubbo 核验文件并获取短期访问链接。
 *
 * @author ninestick
 */
public interface IFileApi {
    /** 返回指定用户且用途一致的 READY 文件；不存在或不符合条件返回失败结果。 */
    RpcResult<ReadyFileDto> getReadyFile(String fileId, Long ownerUserId, String typeCode);

    /** 本人 READY 证件照的两小时读取链接；用途固定，不接受对象路径。 */
    RpcResult<SignedFileUrlDto> getResumePhotoUrl(String fileId, Long ownerUserId);

    /** 批量签发本人 READY 证件照的两小时链接，最多 100 项；一次数据库查询。 */
    RpcResult<Map<String, SignedFileUrlDto>> getResumePhotoUrls(List<String> fileIds, Long ownerUserId);

    /** 按对象 key 批量查询 READY 头像；只签发这些公开头像，最多 100 个，不逐条查询数据库。 */
    RpcResult<Map<String, String>> getAvatarUrls(List<String> objectKeys);

    /** 仅为默认头像固定目录签发批量读取链接，不接受用户上传路径或任意 URL。 */
    RpcResult<Map<String, String>> getDefaultAvatarUrls(List<String> objectKeys);
}
