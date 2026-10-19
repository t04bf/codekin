package org.ninestick.codekin.file.service;

import java.util.List;
import java.util.Map;
import org.ninestick.codekin.file.domain.dto.UploadSignatureDto;
import org.ninestick.codekin.file.domain.vo.UploadSignatureVo;
import org.ninestick.codekin.model.file.dto.ReadyFileDto;
import org.ninestick.codekin.model.file.dto.SignedFileUrlDto;

/**
 * 文件应用服务，身份由 HTTP 网关上下文或受保护的内部 RPC 调用传入。
 *
 * @author ninestick
 */
public interface IFileService {
    /** 根据枚举 code 创建受限 POST 上传凭证。 */
    UploadSignatureVo createSignature(UploadSignatureDto request, Long ownerUserId);
    /** 核验并转换上传，返回正式文件；已完成的同一记录可重复确认。 */
    ReadyFileDto complete(Long fileId, Long ownerUserId);
    /** 核验文件归属、用途与 READY 状态。 */
    ReadyFileDto getReadyFile(Long fileId, Long ownerUserId, String typeCode);
    /** 仅签发当前用户已确认的证件照，固定两小时有效期。 */
    SignedFileUrlDto getResumePhotoUrl(Long fileId, Long ownerUserId);
    /** 一次查询核验全部证件照归属、用途及 READY 状态，再签发两小时链接。 */
    Map<String, SignedFileUrlDto> getResumePhotoUrls(List<Long> fileIds, Long ownerUserId);
    /** 批量查询正式头像并签发访问地址，禁止数据库 N+1。 */
    Map<String, String> getAvatarUrls(List<String> objectKeys);
    /** 批量签发预置头像链接，严格限制命名空间，不查询用户上传记录。 */
    Map<String, String> getDefaultAvatarUrls(List<String> objectKeys);
}
