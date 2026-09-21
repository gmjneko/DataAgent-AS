# AgentScope Java 2.0.2 升级

后端使用正式版 `2.0.2`，要求 JDK 21。模型通过 OpenAI、DashScope、Anthropic、Ollama 独立扩展接入。单例 `HarnessAgent` 接收每次请求独立的 `RuntimeContext`，包含登录用户、会话、数据源和 traceId；工具不再依赖请求线程的 ThreadLocal。

## 部署和升级

1. 备份现有数据库。对已有数据库执行 `sql/migration_agentscope_v2.sql`（MySQL 5.7 兼容，可重复执行）。全新数据库使用 `sql/data_source.sql`；Docker 初始化目录也会执行幂等迁移。
2. 设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、模型 `IO_GITHUB_MALONETALK_MODEL_API_KEY`、`JWT_SECRET`（至少 32 字节）、`ADMIN_INIT_PASSWORD`。不要将真实凭据写回 properties 或提交 `.env`。
3. 配置 `AGENTSCOPE_WORKSPACE` 为后端可写的持久化目录。Docker Compose 使用 `data-agent-workspace` 卷。备份和恢复必须同时包含数据库与 workspace。
4. 启动一个后端实例。本次不支持多个实例共享会话；进程内按会话串行执行输入校验、Agent 调用和删除，不同会话并行。
5. 登录超级管理员账号，在“系统管理 → MCP 管理”配置服务；连接状态与最近错误可用于排查部署环境、命令或服务不可达的问题。

旧 `agentscope_sessions` 表保留用于回滚或人工清理。新代码只使用独立的 `agentscope_agent_state`，不会继续旧 1.x 会话。旧业务报表、导出和会话归属表保留。不要将 `AGENTSCOPE_STATE_TABLE` 指回旧表。回滚应用前同时恢复相应数据库及工作区备份。

## 会话与历史

`MysqlAgentStateStore` 按 `(userId, sessionId)` 存储 AgentState，保存当前模型上下文和工具状态；权限规则在每次加载时按当前应用工具与已注册 MCP 工具刷新。应用要求业务 sessionId 全局唯一，拒绝其他用户占用相同 ID。

AgentScope 的压缩日志位于 workspace。应用额外保存完整结构化消息日志 `<workspace>/<userId>/agents/data-agent/sessions/<sessionId>.messages.jsonl`，保留文本、思考、工具参数和工具结果，供历史接口和报表卡片使用。压缩前先归档消息，大结果卸载后不覆盖历史原始结果。清空会话调用 `clearContext`，删除新状态、历史日志和归属绑定；已有报表和导出沿用原业务清理规则。

`ask_user` 是外部工具。2.0.2 的 `RUNNING` 工具结果转换为前端 `question`，也支持后续 2.x 的 `RequireExternalExecutionEvent`。回答以 `SUCCESS` ToolResultMessage 续接；服务端校验未完成问题及工具名，拒绝重复或伪造工具结果。刷新页面可恢复待回答的问题。

| 环境变量 | 默认值 | 含义 |
| --- | --- | --- |
| AGENTSCOPE_WORKSPACE | .agentscope/workspace | 持久化工作目录 |
| AGENTSCOPE_STATE_TABLE | agentscope_agent_state | 新状态表（自定义时需创建同构表） |
| AGENTSCOPE_MAX_ITERS | 10 | 每轮最大推理次数 |
| AGENTSCOPE_TRIGGER_MESSAGES | 30 | 消息压缩触发数 |
| AGENTSCOPE_KEEP_MESSAGES | 10 | 压缩保留的尾部消息数 |
| AGENTSCOPE_TRIGGER_TOKENS | 32000 | token 压缩触发阈值 |
| AGENTSCOPE_MAX_TOOL_RESULT_CHARS | 80000 | 大工具结果卸载阈值 |
| AGENTSCOPE_MCP_ENABLED | true | MCP 启动加载及动态注册总开关 |

完整历史不作为下一次模型输入；模型使用压缩后的状态。卸载结果由 Harness 写入隔离工作区，通过 `read_file` 按需读取。内置 shell、子 Agent、记忆工具和 tools.json 自动注册关闭；业务 Python 工具仍运行于后端主机，保留 30 秒超时和最多 5 个并发执行，输出上限 2 MB。

## Skills 与 MCP

Skills 使用 AgentSkillRepository，支持 filesystem、Git、classpath、Nacos。Nacos 必须配置 `skillNames`，不进行全量枚举。沿用 `skill.properties` 中的来源与凭据环境配置。

MCP 支持 STDIO、SSE、Streamable HTTP（API 的 transportType 为 `HTTP`）。STDIO 命令在后端环境执行，部署时需安装相应可执行程序；HTTP 支持 headers、queryParams、HTTP 版本、连接/请求/初始化超时。时间单位均为毫秒，范围 1–600000。重定向支持 `NEVER` / `FOLLOW`；不支持 `ERROR` 或 elicitation 表单，会明确拒绝配置。

允许工具列表为空代表全部，禁用列表优先。2.0.2 没有工具名前缀能力，因此同名工具连接会失败，避免覆盖已有工具。失败的服务不阻断启动；修改、启用、停用和删除自动更新工具及权限。重连使用管理接口显式触发，进程内串行化生命周期操作，避免重叠注册。手动断开不修改 ACTIVE 状态，重启后会重新连接。

所有已启用 MCP 工具可供所有登录用户调用，无逐次确认。只有超级管理员可管理配置；env、headers、queryParams 的值在 API 返回和页面中脱敏，编辑时 `********` 保留旧值，删除条目会删除对应配置。含凭据的 URL userinfo 不允许使用，请改用请求头。

API 基础路径 `/api/mcp-server`：CRUD、`PUT /{id}/enable|disable`、`GET /{id}/connection`、`GET /{id}/tools`、`POST /{id}/connect|disconnect|refresh|test`。测试连接不会注册工具。最近连接时间和错误为当前进程的运行状态；数据库保存配置。

## 验证

后端运行 `mvn verify`；前端运行 `npm run build` 和 `node --test tests/*.test.mjs`。本地确定性模型测试覆盖真实 Harness 的外部工具恢复、压缩、事件转换和会话并发；MCP 使用客户端替身验证传输参数、过滤、生命周期和失败清理。真实 MySQL、模型及外部 MCP 服务的联调需在配置完成的部署环境执行。按本次要求不进行 Docker 镜像构建测试。

可选 MySQL 集成测试通过 `AGENTSCOPE_TEST_DB_URL`、`AGENTSCOPE_TEST_DB_USERNAME`、`AGENTSCOPE_TEST_DB_PASSWORD` 启用，要求独立数据库名以 `_test` 结尾，测试只创建并删除随机命名的测试表。未配置时自动跳过。本次按要求只进行本地测试和构建，未连接真实数据库、模型或外部 MCP 服务。
