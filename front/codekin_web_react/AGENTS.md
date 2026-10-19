# React front end

- Independent React + TypeScript + Vite application. Do not introduce Vue.
- Radix/shadcn, Tailwind CSS, Framer Motion; React Hook Form + Zod for forms, TanStack Query for server state, Zustand for sessions.
- Preserve routes, backend contracts and account UX. Profile settings: grouped clickable rows and edit dialogs.
- Uniform Sonner messages; never persist passwords, codes or access tokens. Keep hasPassword mandatory and clear session after password changes.
- Templates copied from Magic Resume. Preserve layouts and attribution. Verify each stage before the next.
- 当前处于开发阶段。没有已上线接口或实际旧数据约束时，直接完善当前协议与实现，不保留废弃草案、不预设旧版兼容、不因开发中调整默认新增第二套协议或模板种子。简历并发保存版本仍须保留。

- 简历仅在后端持久化。前端编辑、撤销历史与待重试请求仅存在于内存，不使用 IndexedDB、localStorage 或 sessionStorage 保存简历，不添加本地草稿入口。

## 页面文案

- 禁止将开发要求、设计要求、页面布局说明、实现细节、技术选型、迁移过程或给开发者的备注写入用户界面。例如“当前头像单独展示”“与默认头像分开展示”属于实现要求，不能作为页面提示。
- 页面文案只保留用户完成操作所需的信息：标题、字段名称、必要的操作指引、实际限制、状态反馈及可采取行动的错误提示。不要添加解释页面如何组织、复述显而易见的功能或汇报已实现需求的说明文字。
- 与用户操作无关的说明放在代码注释或开发文档中；不能通过隐藏文字或无障碍描述变相保留。无障碍名称和描述只用于识别控件及说明操作。
