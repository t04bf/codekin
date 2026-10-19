package org.ninestick.codekin.resume.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.dubbo.config.annotation.DubboReference;
import org.ninestick.codekin.dubbo.api.file.FileRpcConstants;
import org.ninestick.codekin.dubbo.api.file.IFileApi;
import org.ninestick.codekin.model.common.*;
import org.ninestick.codekin.model.file.enums.UploadType;
import org.ninestick.codekin.model.file.dto.SignedFileUrlDto;
import org.ninestick.codekin.security.UserContextHolder;
import org.ninestick.codekin.resume.domain.dto.ResumeSaveDto;
import org.ninestick.codekin.resume.domain.dto.ResumeDeleteDto;
import org.ninestick.codekin.resume.domain.entity.*;
import org.ninestick.codekin.resume.domain.enums.*;
import org.ninestick.codekin.resume.domain.vo.*;
import org.ninestick.codekin.resume.mapper.*;
import org.ninestick.codekin.resume.document.ResumeDocumentValidator;
import org.ninestick.codekin.resume.service.IResumeService;
import org.ninestick.codekin.utils.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * 当前状态使用整份 JSON 保存，身份过滤与旧版本条件同时进入 SQL。
 * 列表模板和证件照批量关联；历史快照只在创建或显式版本点写入，并与保存共用事务。
 * @author ninestick
 */
@Service
public class ResumeServiceImpl implements IResumeService {
    @Resource private UserResumeMapper resumeMapper;
    @Resource private ResumeTemplateMapper templateMapper;
    @Resource private UserResumeRevisionMapper revisionMapper;
    @Resource private ResumeDocumentValidator documentValidator;
    /** 删除参数约束也在应用边界执行，供非 HTTP 调用使用。 */
    @Resource private Validator requestValidator;
    /** 只通过文件服务核验归属、用途及 READY 状态，不能跨模块访问文件表。 */
    @DubboReference(group = FileRpcConstants.GROUP, version = FileRpcConstants.VERSION, check = false, timeout = 10000, retries = 0)
    private IFileApi fileApi;

    @Override
    public List<ResumeTemplateVo> getTemplates() {
        UserContextHolder.requireUserId();
        return templateMapper.selectList(Wrappers.<ResumeTemplateEntity>lambdaQuery()
                .eq(ResumeTemplateEntity::getSchemaVersion, ResumeDocumentValidator.SCHEMA_VERSION)
                .eq(ResumeTemplateEntity::getStatus, ResumeTemplateStatus.PUBLISHED.getCode())
                .orderByAsc(ResumeTemplateEntity::getSortOrder, ResumeTemplateEntity::getId)).stream()
                .map(template -> ResumeTemplateVo.builder().id(template.getId().toString()).templateKey(template.getTemplateKey())
                        .templateVersion(template.getTemplateVersion()).name(template.getName()).description(template.getDescription())
                        .schemaVersion(template.getSchemaVersion()).defaultContent(JsonUtils.fromJson(template.getDefaultContent(), JsonNode.class))
                        .defaultLayout(JsonUtils.fromJson(template.getDefaultLayout(), JsonNode.class)).build()).toList();
    }

    @Override
    public ResumePageVo getResumes(int page, int pageSize) {
        Long owner = UserContextHolder.requireUserId();
        if (page < 1 || page > 10000 || pageSize < 1 || pageSize > 100) throw new BusinessException(CommonCode.INVALID_ARGUMENT);
        // 只读取预览所需的当前文档，不加载历史版本及 mutation 指纹。
        var fetched = resumeMapper.selectList(Wrappers.<UserResumeEntity>lambdaQuery()
                .select(UserResumeEntity::getId, UserResumeEntity::getTitle, UserResumeEntity::getTemplateId,
                        UserResumeEntity::getSchemaVersion, UserResumeEntity::getVersion,
                        UserResumeEntity::getContent, UserResumeEntity::getLayout,
                        UserResumeEntity::getCreatedAt, UserResumeEntity::getUpdatedAt)
                .eq(UserResumeEntity::getUserId, owner).isNull(UserResumeEntity::getDeletedAt)
                .orderByDesc(UserResumeEntity::getUpdatedAt, UserResumeEntity::getId)
                .last("LIMIT " + (long) (page - 1) * pageSize + ", " + (pageSize + 1)));
        var items = fetched.subList(0, Math.min(pageSize, fetched.size()));
        var templateIds = items.stream().map(UserResumeEntity::getTemplateId).collect(Collectors.toSet());
        Map<Long, ResumeTemplateEntity> templates = templateIds.isEmpty() ? Map.of()
                : templateMapper.selectList(Wrappers.<ResumeTemplateEntity>lambdaQuery()
                        .select(ResumeTemplateEntity::getId, ResumeTemplateEntity::getTemplateKey, ResumeTemplateEntity::getTemplateVersion)
                        .in(ResumeTemplateEntity::getId, templateIds)).stream()
                        .collect(Collectors.toMap(ResumeTemplateEntity::getId, Function.identity()));
        Map<Long, JsonNode> contents = new HashMap<>();
        Set<String> photoIds = new HashSet<>();
        for (var resume : items) {
            var content = JsonUtils.fromJson(resume.getContent(), JsonNode.class);
            contents.put(resume.getId(), content);
            var fileId = content.path("basic").path("photoFileId");
            if (!fileId.isNull() && !fileId.isMissingNode()) photoIds.add(fileId.asString());
        }
        Map<String, SignedFileUrlDto> photos = Map.of();
        if (!photoIds.isEmpty()) {
            RpcResult<Map<String, SignedFileUrlDto>> result;
            try { result = fileApi.getResumePhotoUrls(List.copyOf(photoIds), owner); }
            catch (RuntimeException exception) { throw new BusinessException(ResumeCode.FILE_SERVICE_UNAVAILABLE); }
            if (result == null || !result.isSuccess() || result.getData() == null
                    || photoIds.stream().anyMatch(id -> result.getData().get(id) == null)) {
                throw new BusinessException(ResumeCode.FILE_SERVICE_UNAVAILABLE);
            }
            photos = result.getData();
        }
        var signedPhotos = photos;
        var previews = items.stream().map(resume -> {
            var content = contents.get(resume.getId());
            return detail(resume, templates.get(resume.getTemplateId()), content,
                    signedPhotos.get(content.path("basic").path("photoFileId").asString()));
        }).toList();
        return ResumePageVo.builder().items(previews).page(page).pageSize(pageSize).hasMore(fetched.size() > pageSize).build();
    }

    @Override
    public ResumeDetailVo getResume(Long resumeId) {
        var resume = ownedResume(resumeId, UserContextHolder.requireUserId(), false);
        var content = JsonUtils.fromJson(resume.getContent(), JsonNode.class);
        SignedFileUrlDto photo = null;
        var fileId = content.path("basic").path("photoFileId");
        if (!fileId.isNull()) {
            RpcResult<SignedFileUrlDto> result;
            try { result = fileApi.getResumePhotoUrl(fileId.asString(), resume.getUserId()); }
            catch (RuntimeException exception) { throw new BusinessException(ResumeCode.FILE_SERVICE_UNAVAILABLE); }
            if (result == null || !result.isSuccess() || result.getData() == null) {
                throw new BusinessException(ResumeCode.FILE_SERVICE_UNAVAILABLE);
            }
            photo = result.getData();
        }
        return detail(resume, templateMapper.selectById(resume.getTemplateId()), content, photo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeDetailVo createResume(ResumeSaveDto request) {
        Long owner = UserContextHolder.requireUserId();
        String hash = documentValidator.validateAndHash(request);
        if (request.getExpectedVersion() != null) throw new BusinessException(CommonCode.INVALID_ARGUMENT);
        var template = writableTemplate(request, null);
        verifyPhoto(request, owner);
        var now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
        var resume = UserResumeEntity.builder().id(IdWorker.getId()).userId(owner).title(request.getTitle().strip())
                .templateId(template.getId()).schemaVersion(request.getSchemaVersion())
                .content(JsonUtils.toJson(request.getContent())).layout(JsonUtils.toJson(request.getLayout()))
                .version(1L).lastMutationId(request.getMutationId()).lastMutationHash(hash).createdAt(now).updatedAt(now).build();
        if (resumeMapper.insert(resume) != 1) throw new BusinessException(CommonCode.INTERNAL_ERROR);
        checkpoint(resume, ResumeRevisionReason.CREATE);
        return detail(resume, template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResumeDetailVo saveResume(Long resumeId, ResumeSaveDto request) {
        Long owner = UserContextHolder.requireUserId();
        String hash = documentValidator.validateAndHash(request);
        if (request.getExpectedVersion() == null) throw new BusinessException(CommonCode.INVALID_ARGUMENT);
        var current = ownedResume(resumeId, owner, false);
        if (request.getMutationId().equals(current.getLastMutationId())) {
            if (!hash.equals(current.getLastMutationHash())) throw new BusinessException(ResumeCode.MUTATION_REUSED);
            return detail(current, templateMapper.selectById(current.getTemplateId()));
        }
        long expected = Long.parseLong(request.getExpectedVersion());
        if (expected != current.getVersion() || expected == Long.MAX_VALUE) throw new BusinessException(ResumeCode.VERSION_CONFLICT);
        var template = writableTemplate(request, current.getTemplateId());
        verifyPhoto(request, owner);
        var now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
        String content = JsonUtils.toJson(request.getContent()), layout = JsonUtils.toJson(request.getLayout());
        int updated = resumeMapper.update(Wrappers.<UserResumeEntity>lambdaUpdate()
                .eq(UserResumeEntity::getId, resumeId).eq(UserResumeEntity::getUserId, owner)
                .isNull(UserResumeEntity::getDeletedAt).eq(UserResumeEntity::getVersion, expected)
                .set(UserResumeEntity::getTitle, request.getTitle().strip()).set(UserResumeEntity::getTemplateId, template.getId())
                .set(UserResumeEntity::getSchemaVersion, request.getSchemaVersion()).set(UserResumeEntity::getContent, content)
                .set(UserResumeEntity::getLayout, layout).set(UserResumeEntity::getVersion, expected + 1)
                .set(UserResumeEntity::getLastMutationId, request.getMutationId()).set(UserResumeEntity::getLastMutationHash, hash)
                .set(UserResumeEntity::getUpdatedAt, now));
        if (updated != 1) {
            // MySQL RR 下普通查询可能仍读到旧快照；锁定读取得实际最新状态，只用于判断并发重试。
            var latest = ownedResume(resumeId, owner, true);
            if (request.getMutationId().equals(latest.getLastMutationId())) {
                if (!hash.equals(latest.getLastMutationHash())) throw new BusinessException(ResumeCode.MUTATION_REUSED);
                return detail(latest, templateMapper.selectById(latest.getTemplateId()));
            }
            throw new BusinessException(ResumeCode.VERSION_CONFLICT);
        }
        current.setTitle(request.getTitle().strip()); current.setTemplateId(template.getId());
        current.setSchemaVersion(request.getSchemaVersion()); current.setContent(content); current.setLayout(layout);
        current.setVersion(expected + 1); current.setLastMutationId(request.getMutationId());
        current.setLastMutationHash(hash); current.setUpdatedAt(now);
        if (request.isCheckpoint()) checkpoint(current, ResumeRevisionReason.MANUAL);
        return detail(current, template);
    }

    /** 按归属与当前版本删除，允许已成功删除的安全重试。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteResume(Long resumeId, ResumeDeleteDto request) {
        Long owner = UserContextHolder.requireUserId();
        if (request == null || !requestValidator.validate(request).isEmpty()) throw new BusinessException(CommonCode.INVALID_ARGUMENT);
        long expected = Long.parseLong(request.getExpectedVersion());
        // 锁定读使重复删除可返回成功，并与更新 CAS 争用同一行。
        var current = resumeMapper.selectOne(Wrappers.<UserResumeEntity>lambdaQuery()
                .eq(UserResumeEntity::getId, resumeId).eq(UserResumeEntity::getUserId, owner).last("FOR UPDATE"));
        if (current == null) throw new BusinessException(ResumeCode.NOT_FOUND);
        if (current.getDeletedAt() != null) return;
        if (current.getVersion() != expected) throw new BusinessException(ResumeCode.VERSION_CONFLICT);
        int updated = resumeMapper.update(Wrappers.<UserResumeEntity>lambdaUpdate()
                .eq(UserResumeEntity::getId, resumeId).eq(UserResumeEntity::getUserId, owner)
                .isNull(UserResumeEntity::getDeletedAt).eq(UserResumeEntity::getVersion, expected)
                .set(UserResumeEntity::getDeletedAt, LocalDateTime.now(ZoneOffset.UTC))
                .set(UserResumeEntity::getVersion, expected + 1));
        if (updated != 1) throw new BusinessException(ResumeCode.VERSION_CONFLICT);
    }

    /** 所有详情查询都包含归属与未删除条件，不泄漏他人资源是否存在。 */
    private UserResumeEntity ownedResume(Long id, Long owner, boolean lock) {
        if (id == null || id <= 0) throw new BusinessException(ResumeCode.NOT_FOUND);
        var query = Wrappers.<UserResumeEntity>lambdaQuery().eq(UserResumeEntity::getId, id)
                .eq(UserResumeEntity::getUserId, owner).isNull(UserResumeEntity::getDeletedAt);
        if (lock) query.last("FOR UPDATE");
        var resume = resumeMapper.selectOne(query);
        if (resume == null) throw new BusinessException(ResumeCode.NOT_FOUND);
        if (!Integer.valueOf(ResumeDocumentValidator.SCHEMA_VERSION).equals(resume.getSchemaVersion())) throw new BusinessException(ResumeCode.UNSUPPORTED_SCHEMA);
        return resume;
    }

    /** 新建与切换只接受已发布模板；保留停用模板的既有简历允许继续保存。 */
    private ResumeTemplateEntity writableTemplate(ResumeSaveDto request, Long currentTemplateId) {
        var template = templateMapper.selectById(Long.parseLong(request.getTemplateId()));
        boolean published = template != null && ResumeTemplateStatus.PUBLISHED.getCode().equals(template.getStatus());
        boolean retained = template != null && template.getId().equals(currentTemplateId)
                && ResumeTemplateStatus.RETIRED.getCode().equals(template.getStatus());
        if ((!published && !retained) || !Objects.equals(template.getSchemaVersion(), request.getSchemaVersion())) {
            throw new BusinessException(ResumeCode.TEMPLATE_UNAVAILABLE);
        }
        return template;
    }

    /** 单个证件照一次 RPC；正文不持久化客户端预览或短期签名 URL。 */
    private void verifyPhoto(ResumeSaveDto request, Long owner) {
        var reference = request.getContent().path("basic").path("photoFileId");
        if (reference.isNull()) return;
        var id = reference.asString();
        RpcResult<org.ninestick.codekin.model.file.dto.ReadyFileDto> result;
        try { result = fileApi.getReadyFile(id, owner, UploadType.RESUME_PHOTO.getCode()); }
        catch (RuntimeException exception) { throw new BusinessException(ResumeCode.FILE_SERVICE_UNAVAILABLE); }
        if (result == null || !result.isSuccess()) {
            if (result != null && CommonCode.NOT_FOUND.getCode().equals(result.getCode())) throw new BusinessException(ResumeCode.PHOTO_NOT_READY);
            throw new BusinessException(ResumeCode.FILE_SERVICE_UNAVAILABLE);
        }
        var file = result.getData();
        if (file == null || !id.equals(file.getFileId()) || !owner.equals(file.getOwnerUserId())
                || !UploadType.RESUME_PHOTO.getCode().equals(file.getTypeCode())) throw new BusinessException(ResumeCode.PHOTO_NOT_READY);
    }

    /** 事务内保留完整文档；自动保存不进入该分支。 */
    private void checkpoint(UserResumeEntity resume, ResumeRevisionReason reason) {
        int inserted = revisionMapper.insert(UserResumeRevisionEntity.builder().id(IdWorker.getId()).resumeId(resume.getId())
                .resumeVersion(resume.getVersion()).title(resume.getTitle()).templateId(resume.getTemplateId())
                .schemaVersion(resume.getSchemaVersion()).content(resume.getContent()).layout(resume.getLayout())
                .reason(reason.getCode()).createdAt(resume.getUpdatedAt()).build());
        if (inserted != 1) throw new BusinessException(CommonCode.INTERNAL_ERROR);
    }

    /** 对象 JSON 和字符串 ID 的明确 HTTP 契约。 */
    private ResumeDetailVo detail(UserResumeEntity resume, ResumeTemplateEntity template) {
        return detail(resume, template, JsonUtils.fromJson(resume.getContent(), JsonNode.class), null);
    }

    /** 读取列表或详情时附加访问链接，保存响应不重新申请图片签名。 */
    private ResumeDetailVo detail(UserResumeEntity resume, ResumeTemplateEntity template,
            JsonNode content, SignedFileUrlDto photo) {
        return ResumeDetailVo.builder().id(resume.getId().toString()).title(resume.getTitle())
                .templateId(resume.getTemplateId().toString()).templateKey(template == null ? null : template.getTemplateKey())
                .templateVersion(template == null ? null : template.getTemplateVersion()).schemaVersion(resume.getSchemaVersion())
                .content(content).layout(JsonUtils.fromJson(resume.getLayout(), JsonNode.class))
                .photoUrl(photo == null ? null : photo.getUrl()).photoUrlExpiresAt(photo == null ? null : photo.getExpiresAt())
                .version(resume.getVersion().toString()).createdAt(resume.getCreatedAt().toInstant(ZoneOffset.UTC))
                .updatedAt(resume.getUpdatedAt().toInstant(ZoneOffset.UTC)).build();
    }
}
