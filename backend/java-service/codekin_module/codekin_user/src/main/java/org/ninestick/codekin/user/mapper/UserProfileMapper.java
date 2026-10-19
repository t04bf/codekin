package org.ninestick.codekin.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.ninestick.codekin.user.domain.entity.info.UserProfileEntity;

/**
 * 用户资料表的单表持久化入口。
 *
 * @author ninestick
 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfileEntity> {
}
