# 3x-ui Demo

独立可运行的 demo，用来验证：

1. 通过 SSH 连远程服务器，跑常用 x-ui 运维命令
2. 调 3x-ui 面板的 HTTP API 增删改查 inbound 客户端

不进 nook 业务流程，不被 Spring 扫描（没有 `@Component`），随时可以删。

## 文件

| 文件 | 作用 |
|------|------|
| `DemoConfig.java` | 配置加载（properties 文件 + `-D` 系统属性，后者优先） |
| `SshOps.java` | sshj 封装：执行远程命令；自带 `xuiStatus / restartXui / tailXuiLog / panelInfo / backupDb / xrayProcess` |
| `ThreexUiPanelClient.java` | 面板 HTTP API 封装：登录、`listInbounds / addClient / delClient / getClientTraffic / resetClientTraffic / onlines / serverStatus` |
| `ThreexUiDemoApp.java` | main：把以上串起来跑一遍流程 |
| `3xui-demo.properties.example` | 配置模板（`resources/` 下） |

## 配置

复制 [3xui-demo.properties.example](../../../resources/3xui-demo.properties.example) 为 `3xui-demo.properties`，填进真实参数。**别把含密码的版本提交进库**。

最少需要：

- `ssh.host` / `ssh.user`
- `ssh.password` 或 `ssh.privateKeyPath`（二选一）
- `panel.baseUrl` / `panel.username` / `panel.password`

`panel.baseUrl`：如果 3x-ui 在 `x-ui setting` 里配置了 `webBasePath`（默认空），URL 是 `http://host:port/<webBasePath>`，`webBasePath` 如果没配就只到端口。如果要确认，登 SSH 后跑 `x-ui setting -show true` 能看到端口和 webBasePath。

## 运行

### 方式 1：IDE 直接跑

`Run` → 选 `ThreexUiDemoApp.main`，Program arguments 填 `3xui-demo.properties` 的绝对路径或工作目录相对路径。

### 方式 2：Maven exec

```bash
mvn -pl nook-biz-module/nook-biz-xray -am compile
mvn -pl nook-biz-module/nook-biz-xray exec:java \
  -Dexec.mainClass=com.nook.biz.xray.demo.ThreexUiDemoApp \
  -Dexec.args=3xui-demo.properties
```

第一次会把 nook-common / nook-biz-resource / sshj / fastjson2 都编译/拉下来。

### 方式 3：全部走 -D 不要 properties 文件

```bash
mvn -pl nook-biz-module/nook-biz-xray exec:java \
  -Dexec.mainClass=com.nook.biz.xray.demo.ThreexUiDemoApp \
  -Dssh.host=1.2.3.4 -Dssh.user=root -Dssh.password=secret \
  -Dpanel.baseUrl=http://1.2.3.4:2053/abc \
  -Dpanel.username=admin -Dpanel.password=admin
```

## 输出（成功大致长这样）

```
== 3x-ui demo 起步 ==
ssh   = root@1.2.3.4:22
panel = http://1.2.3.4:2053/abc (user=admin)

--- [1] SSH 连接 ---
hostname: us-edge-01
uname:    Linux 5.15.0-x86_64
[x-ui status]
✔ x-ui is running
...

--- [2] 面板登录 ---
登录成功

--- [3] 列出 inbound ---
  #0 id=1 remark=trial protocol=vless port=443 enable=true

--- [4] 在 inbound#1 添加测试客户端 demo-1715000000 ---
addClient: success=true msg=Client(s) added

--- [5] 查测试客户端的流量 ---
traffic: {"id":42,"inboundId":1,"enable":true,"email":"demo-...","up":0,"down":0,...}

--- [6] 删除测试客户端 ---
delClient: success=true msg=Client deleted

--- [7] 当前在线 ---
onlines: []

--- [8] 服务器状态 ---
cpu=2.3% mem=812M/2.0G disk=8.0G/40G

== Demo 结束 ==
```

## 把它接进 nook 业务

`SshOps` / `ThreexUiPanelClient` 这两个类是无状态包装器，可以直接抄到 `xray` 模块的 `service/impl/` 下当 `@Service`，构造参数从 `xray_node` 表读，然后用 nook-common 的 `BusinessException` 替换 `IOException`。`DemoConfig` 和 `ThreexUiDemoApp` 是 demo 专属，正式接入时丢掉。

## 安全注意

- `SshOps` 用 `PromiscuousVerifier()` 接受任何主机指纹；生产里**必须**换成 `OpenSSHKnownHosts` 或显式指纹白名单，否则中间人攻击会得手。
- `panel.ignoreTls=true` 会跳过 HTTPS 证书校验，仅 demo 用；生产应该用合法证书 + 默认校验。
- 别把 `3xui-demo.properties`（含密码）提交进 git；建议在仓库根 `.gitignore` 加一行 `3xui-demo.properties`。

## 没做的

- 没做 inbound 的创建/修改：3x-ui 那边的 `add / update` 接口入参字段非常复杂（每种协议要构造各自的 `streamSettings`/`sniffing`），demo 演不出代表性，先用面板手工建 inbound。
- 没做 SSH 本地端口转发：如果你的面板只监听 127.0.0.1:2053，可以扩 `SshOps` 加一个 `localForward(localPort, "127.0.0.1", 2053)`，把 `panelBaseUrl` 写成 `http://127.0.0.1:<localPort>` 即可。
- 没做客户端流量周期性同步：业务里这块要结合 `monitor_traffic_snapshot` 表与 `getClientTraffics`/`resetClientTraffic` 拼一个 reconcile job，超出 demo 范围。
