package org.ninestick.codekin.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.ninestick.codekin.dubbo.api.file.IFileApi;
import org.ninestick.codekin.dubbo.api.file.FileRpcConstants;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.model.common.CommonCode;
import org.ninestick.codekin.model.common.RpcResult;
import org.ninestick.codekin.model.file.dto.ReadyFileDto;
import org.ninestick.codekin.model.file.constants.DefaultAvatarConstants;
import org.ninestick.codekin.model.file.enums.UploadType;
import org.ninestick.codekin.security.UserContextHolder;
import org.ninestick.codekin.user.domain.dto.info.AvatarUpdateDto;
import org.ninestick.codekin.user.domain.dto.info.DefaultAvatarUpdateDto;
import org.ninestick.codekin.user.domain.vo.info.DefaultAvatarVo;
import org.ninestick.codekin.user.config.UserAvatarProperties;
import org.ninestick.codekin.user.domain.entity.info.*;
import org.ninestick.codekin.user.domain.enums.*;
import org.ninestick.codekin.user.mapper.*;
import org.ninestick.codekin.user.service.IUserAvatarService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 引用已审核的本人上传头像或配置白名单中的预置头像，不直接连接对象存储。
 *
 * @author ninestick
 */
@Slf4j
@Service
public class UserAvatarServiceImpl implements IUserAvatarService {
    /** 延迟连接文件服务，文件服务未启动时不阻断密码登录及资料查询。 */
    @DubboReference(group = FileRpcConstants.GROUP, version = FileRpcConstants.VERSION, check = false, timeout = 10000, retries = 0)
    private IFileApi fileApi;
    /** 账号状态查询。 */
    @Resource private UserAuthMapper authMapper;
    /** 本模块资料持久化。 */
    @Resource private UserProfileMapper profileMapper;
    /** Nacos 管理的预置资源白名单。 */
    @Resource private UserAvatarProperties properties;

    @Override
    public void updateAvatar(AvatarUpdateDto request) {
        Long userId = UserContextHolder.requireUserId();
        UserAuthEntity auth = authMapper.selectById(userId);
        if (auth == null || !UserAccountStatus.NORMAL.getCode().equals(auth.getStatus())) {
            throw new BusinessException(UserCode.ACCOUNT_UNAVAILABLE);
        }
        RpcResult<ReadyFileDto> result;
        try {
            result = fileApi.getReadyFile(request.getFileId(), userId, UploadType.AVATAR.getCode());
        } catch (RuntimeException exception) {
            log.error("头像文件服务核验失败 userId={} exceptionType={}", userId, exception.getClass().getSimpleName());
            throw new BusinessException(UserCode.AVATAR_SERVICE_UNAVAILABLE);
        }
        if (result == null || !result.isSuccess()) {
            if (result != null && CommonCode.NOT_FOUND.getCode().equals(result.getCode())) {
                throw new BusinessException(UserCode.AVATAR_FILE_NOT_READY);
            }
            log.warn("头像文件 RPC 返回失败 userId={} code={}", userId, result == null ? null : result.getCode());
            throw new BusinessException(UserCode.AVATAR_SERVICE_UNAVAILABLE);
        }
        ReadyFileDto file = result.getData();
        if (file == null || !userId.equals(file.getOwnerUserId())
                || !UploadType.AVATAR.getCode().equals(file.getTypeCode()) || !StringUtils.hasText(file.getObjectKey())) {
            throw new BusinessException(UserCode.AVATAR_FILE_NOT_READY);
        }
        int updated = profileMapper.update(Wrappers.<UserProfileEntity>lambdaUpdate()
                .eq(UserProfileEntity::getUserId, userId).set(UserProfileEntity::getAvatarKey, file.getObjectKey())
                .set(UserProfileEntity::getUpdatedAt, LocalDateTime.now(ZoneOffset.UTC)));
        if (updated != 1) throw new BusinessException(UserCode.PROFILE_UPDATE_NOT_COMPLETED);
        log.info("用户头像更新成功 userId={} fileId={}", userId, request.getFileId());
    }

    @Override
    public String getAvatarUrl(String objectKey) {
        if (!StringUtils.hasText(objectKey)) return null;
        try {
            // 默认资源没有用户上传记录，走限定目录的签名入口；历史选择不受目录列表删项影响。
            var result = objectKey.startsWith(DefaultAvatarConstants.OBJECT_KEY_PREFIX)
                    ? fileApi.getDefaultAvatarUrls(List.of(objectKey)) : fileApi.getAvatarUrls(List.of(objectKey));
            if (result == null || !result.isSuccess() || result.getData() == null) {
                log.warn("头像访问 RPC 返回失败 code={}", result == null ? null : result.getCode());
                return null;
            }
            return result.getData().get(objectKey);
        } catch (RuntimeException exception) {
            // 原有登录和资料功能不因对象存储暂时失效而不可用；前端保留吉祥物默认头像。
            log.warn("头像访问地址暂时不可用 exceptionType={}", exception.getClass().getSimpleName());
            return null;
        }
    }

    @Override
    public List<DefaultAvatarVo> getDefaultAvatars() {
        var defaults = List.copyOf(properties.getDefaults());
        if (defaults.isEmpty()) throw new BusinessException(UserCode.DEFAULT_AVATAR_UNAVAILABLE);
        var keys = defaults.stream().map(UserAvatarProperties.DefaultAvatar::getObjectKey).toList();
        RpcResult<java.util.Map<String, String>> result;
        try {
            result = fileApi.getDefaultAvatarUrls(keys);
        } catch (RuntimeException exception) {
            log.error("默认头像签名调用失败 exceptionType={}", exception.getClass().getSimpleName());
            throw new BusinessException(UserCode.AVATAR_SERVICE_UNAVAILABLE);
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw new BusinessException(UserCode.AVATAR_SERVICE_UNAVAILABLE);
        }
        return defaults.stream().map(avatar -> {
            String url = result.getData().get(avatar.getObjectKey());
            if (!StringUtils.hasText(url)) throw new BusinessException(UserCode.AVATAR_SERVICE_UNAVAILABLE);
            return DefaultAvatarVo.builder().name(avatar.getName()).objectKey(avatar.getObjectKey()).url(url).build();
        }).toList();
    }

    @Override
    public void updateDefaultAvatar(DefaultAvatarUpdateDto request) {
        Long userId = UserContextHolder.requireUserId();
        if (properties.getDefaults().stream().noneMatch(avatar -> avatar.getObjectKey().equals(request.getObjectKey()))) {
            throw new BusinessException(UserCode.DEFAULT_AVATAR_INVALID);
        }
        UserAuthEntity auth = authMapper.selectById(userId);
        if (auth == null || !UserAccountStatus.NORMAL.getCode().equals(auth.getStatus())) {
            throw new BusinessException(UserCode.ACCOUNT_UNAVAILABLE);
        }
        int updated = profileMapper.update(Wrappers.<UserProfileEntity>lambdaUpdate()
                .eq(UserProfileEntity::getUserId, userId).set(UserProfileEntity::getAvatarKey, request.getObjectKey())
                .set(UserProfileEntity::getUpdatedAt, LocalDateTime.now(ZoneOffset.UTC)));
        if (updated != 1) throw new BusinessException(UserCode.PROFILE_UPDATE_NOT_COMPLETED);
        log.info("默认头像已设置 userId={}", userId);
    }

    @Override
    public String pickRandomDefaultAvatarKey() {
        var defaults = List.copyOf(properties.getDefaults());
        if (defaults.isEmpty()) throw new BusinessException(UserCode.DEFAULT_AVATAR_UNAVAILABLE);
        // 普通资源随机分配，不是验证码；注册事务不承担 RPC 或对象存储网络调用。
        return defaults.get(ThreadLocalRandom.current().nextInt(defaults.size())).getObjectKey();
    }
}
