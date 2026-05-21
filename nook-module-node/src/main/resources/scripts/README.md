# scripts/ — 远端脚本模板库

后端 SSH 到目标 server 上跑的脚本; Java 端用 `ScriptCatalog` 注册 + 调用.

## 目录约定

| 子目录 | 用途 | 示例 |
| --- | --- | --- |
| `install/` | 一键装机 (单文件, 不依赖模块拼装) | `nook-agent.sh.tmpl`, `socks5-dante.sh.tmpl` |
| `uninstall/` | 卸载脚本 (预留) | — |
| `ops/` | 运维短操作 + 公共 helper | `socks5-update-creds.sh.tmpl`, `_helpers.sh` |
| `modules/` | 可复用拼装单元 (xray install 多模块串接) | `00-prepare-env.sh.tmpl`, `50-xray.sh.tmpl` |

## 占位协议

模板里的 `{{KEY}}` 由 `RemoteScriptRunner.renderTemplate` 做字面量替换 (Hutool ResourceUtil + String.replace).

- key 大写下划线, 例: `{{BACKEND_URL}}`, `{{AGENT_TOKEN}}`
- 必填 placeholder 在 `ScriptCatalog` 里登记到 `ScriptModule.requiredVars`, `run()` 前校验缺一抛 `BACKEND_OPERATION_FAILED`
- 模板里 bash 自带的 `$VAR` 不会被替换, 安全 (Java 只替换 `{{...}}`)

## 新增脚本步骤

1. 文件放对应子目录, 命名 kebab-case + `.sh.tmpl`
2. `ScriptCatalog` 加 `public static final ScriptModule` 常量
3. 加到 `ALL` 列表 (catalog list/byId 才能查到)
4. 调用方拿常量传给 `catalog.run(session, MODULE, vars, timeout, sink)` —— 不要再写 classpath 字符串
