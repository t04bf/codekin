import { DEFAULT_FIELD_ORDER } from "./constants";
import { GlobalSettings, DEFAULT_CONFIG, ResumeData } from "../types/resume";
const initialGlobalSettings: GlobalSettings = {
  baseFontSize: 16,
  pagePadding: 32,
  paragraphSpacing: 12,
  lineHeight: 1.5,
  sectionSpacing: 10,
  headerSize: 18,
  subheaderSize: 16,
  useIconMode: true,
  themeColor: "#000000",
  centerSubtitle: true,
  pageBreakLinesVisible: true,
};

export const initialResumeState = {
  title: "新建简历",
  basic: {
    name: "codekin",
    title: "高级后端开发工程师",
    employementStatus: "离职",
    email: "codekin@example.com",
    phone: "13800138000",
    location: "北京市朝阳区",
    birthDate: "1995-01",
    fieldOrder: DEFAULT_FIELD_ORDER,
    icons: {
      email: "Mail",
      phone: "Phone",
      birthDate: "CalendarRange",
      employementStatus: "Briefcase",
      location: "MapPin",
    },
    photoConfig: DEFAULT_CONFIG,
    customFields: [
      {
        id: "personal",
        label: "个人网站",
        value: "https://codekin.example.com",
        icon: "Globe",
      },
    ],
    photo: "/resume-avatar.png",
    githubKey: "",
    githubUseName: "",
    githubContributionsVisible: false,
  },
  education: [
    {
      id: "1",
      school: "北京大学",
      major: "计算机科学与技术",
      degree: "",
      startDate: "2013-09",
      endDate: "2017-06",
      visible: true,
      gpa: "",
      description: `<ul>
        <li>主修课程：数据结构、算法设计、操作系统、计算机网络、数据库系统</li>
        <li>专业排名前 5%，连续三年获得一等奖学金</li>
        <li>担任计算机协会技术部部长，组织多次技术分享会</li>
        <li>参与开源项目贡献，获得 GitHub Campus Expert 认证</li>
      </ul>`,
    },
  ],
  skillContent: `<div class="skill-content">
  <ul>
    <li>开发语言：Java，熟悉集合、并发编程、JVM 内存模型与性能调优</li>
    <li>服务框架：Spring Boot、Spring MVC、Spring Security、MyBatis-Plus</li>
    <li>数据库：MySQL，熟悉索引设计、事务隔离、慢 SQL 分析与查询优化</li>
    <li>缓存与消息：Redis、RabbitMQ，掌握缓存一致性、消息重试与幂等处理</li>
    <li>接口设计：RESTful API、参数校验、统一异常处理、接口版本管理</li>
    <li>分布式系统：熟悉限流、熔断、分布式锁与业务一致性方案</li>
    <li>测试与质量：JUnit、Mockito、Testcontainers，覆盖核心业务与集成场景</li>
    <li>部署与监控：Linux、Docker、GitHub Actions、Prometheus、Grafana</li>
    <li>工程实践：Git、Maven，重视代码审查、技术文档与可维护的模块设计</li>
  </ul>
</div>`,
  selfEvaluationContent: "",
  experience: [
    {
      id: "1",
      company: "Codekin 科技",
      position: "高级后端开发工程师",
      date: "2021.07 - 2024.12",
      visible: true,
      details: `<ul>
      <li>负责用户中心与业务平台的后端开发，完成领域建模、接口设计及服务交付</li>
      <li>优化 MySQL 索引与批量查询，消除 N+1 查询，降低高频接口响应耗时</li>
      <li>建设 Redis 缓存与消息队列机制，支持异步任务、失败重试和幂等消费</li>
      <li>完善权限校验、审计日志与监控告警，提高服务稳定性和故障定位效率</li>
      <li>维护自动化测试与持续集成流程，参与代码审查和团队技术分享</li>
    </ul>`,
    },
  ],
  draggingProjectId: null,
  projects: [
    {
      id: "p1",
      name: "用户中心与权限服务",
      role: "后端负责人",
      date: "2022.06 - 2023.12",
      description: `<ul>
        <li>基于 Spring Boot 与 Spring Security 构建统一用户、认证和授权服务</li>
        <li>实现账号登录、令牌刷新、联系方式绑定与个人资料维护接口</li>
        <li>采用 RBAC 权限模型，统一资源访问校验与用户数据隔离</li>
        <li>使用 Redis 管理验证码与登录限流，记录关键操作审计日志</li>
        <li>通过集成测试验证并发请求、令牌失效和越权访问等边界场景</li>
      </ul>`,
      visible: true,
    },
    {
      id: "p2",
      name: "订单与库存管理平台",
      role: "核心后端开发者",
      date: "2020.03 - 2021.06",
      description: `<ul>
        <li>设计订单、库存与支付状态模型，支持下单、取消和库存释放流程</li>
        <li>使用 MySQL 事务与条件更新控制库存扣减，防止并发超卖</li>
        <li>通过 RabbitMQ 处理异步通知，结合幂等键与重试机制保障消息消费</li>
        <li>优化分页与批量查询，建立覆盖核心业务路径的接口自动化测试</li>
        <li>构建对账与补偿任务，追踪异常订单并恢复业务状态一致性</li>
      </ul>`,
      visible: true,
    },
    {
      id: "p3",
      name: "文件管理与异步任务服务",
      role: "后端技术负责人",
      date: "2021.09 - 2022.03",
      description: `<ul>
        <li>基于 MinIO 提供文件上传、下载与元数据管理，按用户隔离文件访问</li>
        <li>实现预签名上传、文件类型与大小校验，以及上传完成后的状态确认</li>
        <li>设计异步任务状态机，支持进度查询、失败重试与超时恢复</li>
        <li>使用 Docker 与持续集成流程部署服务，统一环境配置和健康检查</li>
        <li>接入 Prometheus 与 Grafana，监控请求延迟、错误率与任务积压</li>
      </ul>`,
      visible: true,
    },
  ],
  menuSections: [
    { id: "basic", title: "基本信息", icon: "👤", enabled: true, order: 0 },
    { id: "skills", title: "专业技能", icon: "⚡", enabled: true, order: 1 },
    {
      id: "experience",
      title: "工作经验",
      icon: "💼",
      enabled: true,
      order: 2,
    },

    {
      id: "projects",
      title: "项目经历",
      icon: "🚀",
      enabled: true,
      order: 3,
    },
    {
      id: "education",
      title: "教育经历",
      icon: "🎓",
      enabled: true,
      order: 4,
    },
  ],
  certificates: [],
  customData: {},
  activeSection: "basic",
  globalSettings: initialGlobalSettings,
};

export const initialResumeStateEn = {
  title: "New Resume",
  basic: {
    name: "codekin",
    title: "Senior Backend Engineer",
    employementStatus: "Available",
    email: "codekin@example.com",
    phone: "555-123-4567",
    location: "San Francisco, CA",
    birthDate: "",
    fieldOrder: DEFAULT_FIELD_ORDER,
    icons: {
      email: "Mail",
      phone: "Phone",
      birthDate: "CalendarRange",
      employementStatus: "Briefcase",
      location: "MapPin",
    },
    photoConfig: DEFAULT_CONFIG,
    customFields: [],
    photo: "/resume-avatar.png",
    githubKey: "",
    githubUseName: "",
    githubContributionsVisible: false,
  },
  education: [
    {
      id: "1",
      school: "Stanford University",
      major: "Computer Science",
      degree: "",
      startDate: "2013-09",
      endDate: "2017-06",
      visible: true,
      gpa: "",
      description: `<ul>
        <li>Core courses: Data Structures, Algorithms, Operating Systems, Computer Networks, Database Systems</li>
        <li>Top 5% of class, received Dean's List honors for three consecutive years</li>
        <li>Served as Technical Director of the Computer Science Association, organized multiple tech workshops</li>
        <li>Contributed to open-source projects, earned GitHub Campus Expert certification</li>
      </ul>`,
    },
  ],
  skillContent: `<div class="skill-content">
  <ul>
    <li>Languages: Java, collections, concurrency, JVM memory model and performance tuning</li>
    <li>Frameworks: Spring Boot, Spring MVC, Spring Security, MyBatis-Plus</li>
    <li>Databases: MySQL, index design, transaction isolation and query optimization</li>
    <li>Caching and Messaging: Redis, RabbitMQ, cache consistency, retries and idempotency</li>
    <li>API Design: RESTful APIs, validation, exception handling and versioning</li>
    <li>Distributed Systems: Rate limiting, circuit breakers, distributed locks and consistency</li>
    <li>Testing: JUnit, Mockito and Testcontainers for unit and integration tests</li>
    <li>Operations: Linux, Docker, GitHub Actions, Prometheus and Grafana</li>
    <li>Engineering: Git, Maven, code reviews, documentation and maintainable modules</li>
  </ul>
</div>`,
  selfEvaluationContent: "",
  experience: [
    {
      id: "1",
      company: "Codekin Technology",
      position: "Senior Backend Engineer",
      date: "2021.07 - 2024.12",
      visible: true,
      details: `<ul>
      <li>Delivered backend services for user identity and business workflows, including domain models and APIs</li>
      <li>Optimized MySQL indexes and batch queries, eliminated N+1 queries and improved API latency</li>
      <li>Built Redis caching and message processing with asynchronous jobs, retries and idempotent consumers</li>
      <li>Implemented authorization, audit logs, monitoring and alerts to improve service reliability</li>
      <li>Maintained automated tests and CI pipelines, reviewed code and shared technical practices</li>
    </ul>`,
    },
  ],
  draggingProjectId: null,
  projects: [
    {
      id: "p1",
      name: "User Identity and Authorization Service",
      role: "Backend Lead",
      date: "2022.06 - 2023.12",
      description: `<ul>
        <li>Built user identity, authentication and authorization services with Spring Boot and Spring Security</li>
        <li>Implemented login, token refresh, contact binding and profile management APIs</li>
        <li>Applied RBAC and ownership checks to isolate user data and protect resources</li>
        <li>Used Redis for verification codes and login rate limits, with audit logs for key actions</li>
        <li>Tested concurrency, expired tokens and unauthorized access through integration tests</li>
      </ul>`,
      visible: true,
    },
    {
      id: "p2",
      name: "Order and Inventory Platform",
      role: "Backend Developer",
      date: "2020.03 - 2021.06",
      description: `<ul>
        <li>Modeled orders, inventory and payment states for checkout, cancellation and stock release</li>
        <li>Used MySQL transactions and conditional updates to prevent overselling under concurrency</li>
        <li>Processed asynchronous notifications with RabbitMQ, idempotency keys and retry policies</li>
        <li>Optimized pagination and batch queries, with automated tests for core workflows</li>
        <li>Built reconciliation and compensation jobs to recover inconsistent order states</li>
      </ul>`,
      visible: true,
    },
    {
      id: "p3",
      name: "File Management and Async Job Service",
      role: "Backend Technical Lead",
      date: "2021.09 - 2022.05",
      description: `<ul>
        <li>Provided uploads, downloads and metadata management with MinIO and per-user access checks</li>
        <li>Implemented presigned uploads, file type and size validation, and completion confirmation</li>
        <li>Designed asynchronous job states for progress tracking, retries and timeout recovery</li>
        <li>Deployed services with Docker and CI pipelines, standardized configuration and health checks</li>
        <li>Monitored request latency, error rates and job backlogs with Prometheus and Grafana</li>
      </ul>`,
      visible: true,
    },
  ],
  menuSections: [
    {
      id: "basic",
      title: "Profile",
      icon: "👤",
      enabled: true,
      order: 0,
    },
    {
      id: "skills",
      title: "Skills",
      icon: "⚡",
      enabled: true,
      order: 1,
    },
    {
      id: "experience",
      title: "Experience",
      icon: "💼",
      enabled: true,
      order: 2,
    },
    {
      id: "projects",
      title: "Projects",
      icon: "🚀",
      enabled: true,
      order: 3,
    },
    {
      id: "education",
      title: "Education",
      icon: "🎓",
      enabled: true,
      order: 4,
    },
  ],
  certificates: [],
  customData: {},
  activeSection: "basic",
  globalSettings: initialGlobalSettings,
};

export const blankResumeState = {
  ...initialResumeState,
  title: "新建简历",
  basic: {
    ...initialResumeState.basic,
    name: "",
    title: "",
    email: "",
    phone: "",
    location: "",
    birthDate: "",
    employementStatus: "",
    photo: "",
    customFields: [],
  },
  education: [],
  skillContent: "",
  selfEvaluationContent: "",
  experience: [],
  projects: [],
  certificates: [],
  menuSections: [initialResumeState.menuSections[0]],
};

export const blankResumeStateEn = {
  ...initialResumeStateEn,
  title: "New Resume",
  basic: {
    ...initialResumeStateEn.basic,
    name: "",
    title: "",
    email: "",
    phone: "",
    location: "",
    birthDate: "",
    employementStatus: "",
    photo: "",
    customFields: [],
  },
  education: [],
  skillContent: "",
  selfEvaluationContent: "",
  experience: [],
  projects: [],
  certificates: [],
  menuSections: [initialResumeStateEn.menuSections[0]],
};
