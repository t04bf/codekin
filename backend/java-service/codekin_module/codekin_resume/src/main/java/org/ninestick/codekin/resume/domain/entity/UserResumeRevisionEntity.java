package org.ninestick.codekin.resume.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 与当前文档在同一事务中写入的不可变快照，版本号允许不连续。
 *
 * @author ninestick
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_resume_revision")
public class UserResumeRevisionEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long resumeId;

    /** 快照对应的简历版本，允许不连续。 */
    private Long resumeVersion;

    private String title;

    private Long templateId;

    /** 该版本正文及布局协议版本。 */
    private Integer schemaVersion;

    /** 完整正文快照；保存原始 JSON 对象文本，业务层负责结构校验。 */
    private String content;

    /** 完整布局快照；保存原始 JSON 对象文本，业务层负责结构校验。 */
    private String layout;

    /** CREATE MANUAL CHECKPOINT BEFORE_RESTORE等服务端枚举。 */
    private String reason;

    /** 用户为版本填写的备注。 */
    private String label;

    /** 快照创建时间 UTC。 */
    private LocalDateTime createdAt;
}

