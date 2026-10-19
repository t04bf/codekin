package org.ninestick.codekin.user.service;

import org.ninestick.codekin.user.domain.dto.info.AvatarUpdateDto;
import org.ninestick.codekin.user.domain.dto.info.DefaultAvatarUpdateDto;
import org.ninestick.codekin.user.domain.vo.info.DefaultAvatarVo;
import java.util.List;

/**
 * 用户头像引用及访问链接，与文件存储和核验职责分离。
 *
 * @author ninestick
 */
public interface IUserAvatarService {
    /** 通过 Dubbo 核验文件后更新当前用户头像。 */
    void updateAvatar(AvatarUpdateDto request);
    /** 返回配置中的头像及批量签发的展示链接。 */
    List<DefaultAvatarVo> getDefaultAvatars();
    /** 将白名单内的默认头像设置给当前正常账号。 */
    void updateDefaultAvatar(DefaultAvatarUpdateDto request);
    /** 注册时随机取得稳定头像路径，不访问对象存储或其他服务。 */
    String pickRandomDefaultAvatarKey();
    /** 查询公开正式头像的短期访问地址，存储不可用时退回默认头像。 */
    String getAvatarUrl(String objectKey);
}
