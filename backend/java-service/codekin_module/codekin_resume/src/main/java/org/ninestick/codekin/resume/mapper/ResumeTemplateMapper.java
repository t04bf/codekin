package org.ninestick.codekin.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.ninestick.codekin.resume.domain.entity.ResumeTemplateEntity;

/**
 * resume_template 单表持久化入口，调用方负责归属、版本和生命周期约束。
 *
 * @author ninestick
 */
@Mapper
public interface ResumeTemplateMapper extends BaseMapper<ResumeTemplateEntity> {
}

