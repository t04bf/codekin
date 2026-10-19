<script setup lang="ts">
import '@arco-design/web-vue/es/grid/style/css.js'
import { Grid as AGrid } from '@arco-design/web-vue'
import brandMark from '@/assets/brand-mark.png'
import { ElAlert, ElAvatar, ElCard, ElCol, ElContainer, ElLink, ElMain, ElRow, ElSpace, ElText } from 'element-plus'
</script>

<template>
  <ElContainer direction="vertical"><ElMain><ElRow justify="center"><ElCol :xs="24" :md="18" :lg="14"><ElCard shadow="never"><AGrid :cols="1" :row-gap="24">
      <ElSpace wrap>
        <ElLink href="/login" type="primary">
          <ElAvatar :src="brandMark" :size="32" shape="square" fit="contain" alt="Codekin Logo" />
          <ElText>Codekin</ElText>
        </ElLink>
        <ElLink href="/login" type="primary">返回登录</ElLink>
      </ElSpace>

      <ElText tag="p">使用规则</ElText>
      <ElText tag="h1" size="large">用户协议</ElText>
      <ElText tag="p">草案 · 2026 年 10 月 2 日</ElText>
      <ElAlert type="warning" title="本页为开发阶段草案。运营主体的法定名称尚未确定，正式对外提供服务前需补齐并公布生效日期。" :closable="false" show-icon />

      <AGrid :cols="1" :row-gap="12">
        <ElText tag="h2" size="large">一、协议适用范围</ElText>
        <ElText tag="p">本协议说明您使用 Codekin 网站和账号服务时的基本规则。当前版本提供手机号、邮箱验证码及已设置密码的账号登录，以及基础用户资料展示。在线练习、简历分析、AI 模拟面试等规划中的功能，只有实际开放后才适用届时展示的具体规则。</ElText>
      </AGrid>

      <AGrid :cols="1" :row-gap="12">
        <ElText tag="h2" size="large">二、账号注册与安全</ElText>
        <ElText tag="p">首次使用有效的手机或邮箱验证码登录时，系统会创建账号。您应使用本人有权使用的手机号或邮箱，妥善保管密码和登录令牌，不得借用、出售或冒用他人账号。</ElText>
        <ElText tag="p">如果发现账号被异常使用，请通过下方邮箱联系。为维护服务与其他用户的安全，平台可在核实后限制异常请求或撤销相关登录会话，并依法处理违规行为。</ElText>
      </AGrid>

      <AGrid :cols="1" :row-gap="12">
        <ElText tag="h2" size="large">三、使用规范</ElText>
        <ElText tag="p">您不得利用本服务实施违法活动，批量注册、恶意刷取验证码、尝试未经授权访问他人账号或干扰系统运行。您提供的昵称、头像等资料不得侵犯他人合法权益。</ElText>
      </AGrid>

      <AGrid :cols="1" :row-gap="12">
        <ElText tag="h2" size="large">四、服务说明与变更</ElText>
        <ElText tag="p">我们会尽力维护服务的稳定与安全，但网络故障、维护或第三方短信、邮件服务异常可能影响使用。产品功能调整、协议实质性变更或新增涉及个人信息的处理场景时，我们会在相应页面告知；依法需要另行取得同意的，将在处理前征求同意。</ElText>
        <ElText tag="p">未来提供的学习建议、岗位分析或 AI 生成内容仅供参考，不构成录用承诺或专业保证；实际求职决定由您结合自身情况作出。</ElText>
      </AGrid>

      <AGrid :cols="1" :row-gap="12">
        <ElText tag="h2" size="large">五、个人信息与联系</ElText>
        <ElText tag="p">账号信息的处理方式见 <ElLink href="/privacy" type="primary">《隐私政策》</ElLink>。对本协议或账号使用有疑问，可发送邮件至 <ElLink href="mailto:15815221751@163.com" type="primary">15815221751@163.com</ElLink>。</ElText>
      </AGrid>
    </AGrid></ElCard></ElCol></ElRow></ElMain></ElContainer>
</template>
