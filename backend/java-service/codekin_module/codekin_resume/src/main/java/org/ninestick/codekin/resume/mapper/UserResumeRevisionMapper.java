package org.ninestick.codekin.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.ninestick.codekin.resume.domain.entity.UserResumeRevisionEntity;

/**
 * user_resume_revision 单表持久化入口，调用方负责归属、版本和生命周期约束。
 *
 * @author ninestick
 */
@Mapper
public interface UserResumeRevisionMapper extends BaseMapper<UserResumeRevisionEntity> {
}

