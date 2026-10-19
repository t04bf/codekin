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

      <ElText tag="p">个人信息处理说明</ElText>
      <ElText tag="h1" size="large">隐私政策</ElText>
      <ElText tag="p">草案 · 2026 年 10 月 2 日</ElText>
      <ElAlert type="warning" title="本页为开发阶段草案。Codekin 是产品名称；运营主体的法定名称、正式部署地点及完整的账号注销流程尚待确认，正式对外提供服务前需补齐。" :closable="false" show-icon />

      <AGrid :cols="1" :row-gap="12">
        <ElText tag="h2" size="large">一、我们处理哪些信息</ElText>
        <AGrid :cols="1" :row-gap="12">
          <ElText tag="p"><ElText tag="strong">账号与验证信息：</ElText>您用于登录或绑定的手机号、邮箱，以及您主动设置的密码。密码以散列值保存；短信和邮件验证码用于核验身份。</ElText>
          <ElText tag="p"><ElText tag="strong">基础资料：</ElText>账号创建时生成的用户 ID 和昵称；您以后主动填写或上传时，可能包括头像、个人简介、性别、生日和真实姓名。这些资料并非登录所必需。用户 ID、昵称、头像和个人简介可通过公开资料接口供他人查看；手机号、邮箱、真实姓名、性别和生日不在该接口返回。</ElText>
          <ElText tag="p"><ElText tag="strong">安全记录：</ElText>最近一次成功登录的 IP 地址和时间，以及为识别会话、限制验证码滥用所需的临时记录。</ElText>
        </AGrid>
        <ElText tag="p">当前版本尚未上线简历解析、编程答题或 AI 面试数据处理功能。相关功能启用前，我们会另行说明所需数据、用途和处理方式。</ElText>
      </AGrid>

      <AGrid :cols="1" :row-gap="12">
        <ElText tag="h2" size="large">二、使用目的与方式</ElText>
        <ElText tag="p">我们使用上述信息完成注册和登录、展示基础资料、发送并核验验证码、维护账号安全及防止恶意请求。您可选择手机或邮箱验证码登录；不提供相应联系方式时，无法使用该种登录方式。</ElText>
      </AGrid>

      <AGrid :cols="1" :row-gap="12">
        <ElText tag="h2" size="large">三、短信、邮件与存储</ElText>
        <ElText tag="p">选择短信验证码时，手机号和验证码会提交给阿里云短信服务用于投递；选择邮箱验证码时，邮箱地址和验证码会通过网易邮箱 SMTP 服务投递。我们不会把密码提供给这两项服务。</ElText>
        <ElText tag="p">浏览器将 Access Token 保存在运行内存中，并将用于维持登录状态的 Refresh Token 保存在本地存储。您退出登录后，前端会清除令牌并请求服务端撤销会话。浏览器脚本若被恶意执行，可能读取本地存储中的令牌，因此请勿在不可信设备上保持登录。</ElText>
      </AGrid>

      <AGrid :cols="1" :row-gap="12">
        <ElText tag="h2" size="large">四、保存时间与安全</ElText>
        <ElText tag="p">验证码及发送限制记录只在实现验证和风控所需的短期内保存。当前登录配置为：Access Token 最长 30 分钟，Refresh Token 空闲 7 天失效，单次会话最长 30 天；部署配置变更时，以服务实际配置为准。</ElText>
        <ElText tag="p">账号及资料在您使用服务期间保存；停止使用或提出删除请求后，将根据实现目的所需的最短期限和适用法律要求处理。数据库备份与日志的具体保存期限尚待运营者确定，正式发布前会在本页明确。</ElText>
      </AGrid>

      <AGrid :cols="1" :row-gap="12">
        <ElText tag="h2" size="large">五、您的权利</ElText>
        <ElText tag="p">登录后，您可以在当前用户页面查看基础资料。若需查询、复制、更正、删除个人信息，或申请注销账号、撤回非必要处理的同意，请通过 <ElLink href="mailto:15815221751@163.com" type="primary">15815221751@163.com</ElLink> 联系，并提供核验账号归属所需的信息。当前版本尚无自助注销入口，我们会在核实后依法处理请求。</ElText>
      </AGrid>

      <AGrid :cols="1" :row-gap="12">
        <ElText tag="h2" size="large">六、未成年人及政策更新</ElText>
        <ElText tag="p">本服务面向 IT 学习与求职用户，现阶段未提供专门面向不满 14 周岁儿童的服务。如需处理儿童个人信息，应先取得监护人同意并制定专门规则。在收集范围、第三方接收方或处理目的发生实质变化时，我们会更新本政策并依法履行告知和同意义务。</ElText>
      </AGrid>

      <AGrid :cols="1" :row-gap="12">
        <ElText tag="h2" size="large">七、联系渠道</ElText>
        <ElText tag="p">隐私问题、权利请求和投诉请发送至 <ElLink href="mailto:15815221751@163.com" type="primary">15815221751@163.com</ElLink>。运营主体的法定名称及其他联系信息待正式发布前补充。</ElText>
      </AGrid>
    </AGrid></ElCard></ElCol></ElRow></ElMain></ElContainer>
</template>
