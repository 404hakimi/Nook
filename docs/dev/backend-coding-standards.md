# Nook 后端开发规范

> 版本: v1.3 ｜ 日期: 2026-05-29 ｜ 适用: 所有 `nook-module-*` 模块

后端开发的强制约束. 本文为 AI 阅读优化, 规则优先于示例.

> 示例代码统一用占位类名 (`SomeDO` / `SomeService` / `SomeEnum` 等), 不绑定具体业务实体, 便于规则沉淀为通用规范.

---

## 0. Nook 项目骨架要点

| 项 | Nook 现状 | 备注 |
|---|---|---|
| 主键 | `CHAR(32)` UUID, `BaseEntity.id` 类型 `String`, `@TableId(type = IdType.ASSIGN_UUID)` | 不用雪花 Long |
| 时间字段 | `created_at` / `updated_at`, 实体字段 `createdAt` / `updatedAt`, `MetaObjectHandlerImpl` 自动 fill | 列名下划线 / 字段名驼峰 |
| 软删除 | `deleted TINYINT` (0/1), 实体 `@TableLogic Integer deleted` | |
| Mapper 基类 | MP 原生 `BaseMapper<T>`, 用 `Wrappers.lambdaQuery()` / `lambdaUpdate()` | **没有** `BaseMapperX` / `LambdaQueryWrapperX` |
| 注入 | `@RequiredArgsConstructor` + `final` 构造注入 | **不用** `@Resource` / `@Autowired` |
| 响应 | `com.nook.common.web.response.Result` + `Result.ok(data)` / `Result.ok()` | 不用 `CommonResult` / `success()` |
| 分页 | `com.nook.common.web.response.PageResult` + `PageResult.of(total, list)` | API 一致 |
| 错误码 | `BusinessException(ErrorCode)`, `ErrorCode` 是接口, 各模块自定义实现常量类 | 不用 `new ErrorCode(数字, "msg")` 数字编码 |
| 鉴权 | sa-token: `StpSystemUtil` (admin) / `StpMemberUtil` (customer) 双体系隔离, loginType `system` / `member` | 不用 Spring Security `@PreAuthorize` |
| HTTP 路径前缀 | admin: `/admin/<module>/<feature>/...`; customer portal: `/portal/<module>/<feature>/...` | 跟 `SaTokenConfig` 一致 |
| Swagger | 起步**不强制**类/方法注解 (`@Tag` / `@Operation`), 但欢迎补 | 后续接 OpenAPI 时再补 |
| 多租户 | **不引入** | Nook 是 SaaS 但单租户产品 |
| 字典体系 | **不引入** | 起步用枚举 + DB 字段 |
| 跨模块 API | 每个业务模块拆 `nook-module-<name>-api` (对外契约: enums / DTO / Api 接口) + `nook-module-<name>-server` (实现); 参考 yudao-cloud simplified | 禁止直接注入其他模块 -server 内部的 Service / Mapper |

---

## 1. 分层职责

```
Controller  → 参数校验、调用 Service、调 Convert 组装 VO、返回 Result
Service     → 业务逻辑、事务、缓存; 校验委托 Validator; 返回 DO, 不构建 VO (跨模块/聚合视图例外: 经 Convert 返 VO, 见下方关联拼接 / §8)
Validator   → 集中存在性 / 唯一性 / 业务前置校验, 抛 BusinessException; 每个含校验逻辑的 Service 配对一个 XxxValidator
Convert     → DO ↔ VO; 接收纯数据 Map / List 拼接, 禁止注入 Service
Mapper      → 数据库访问; 继承 `BaseMapper<T>`, default 方法封装查询 Wrapper
```

### 跨模块调用

每个业务模块拆两 maven 子模块 (yudao-cloud simplified, monolith 单进程不引 Feign):

- `nook-module-<name>-api` — 对外契约, 零业务依赖 (只允许 jakarta.validation / lombok 这类编译期工具). 内含:
  - `com.nook.biz.<name>.enums.<feature>.*` — 跨模块共享的枚举
  - `com.nook.biz.<name>.api.<feature>.{XxxApi, dto.XxxRespDTO, dto.XxxSaveReqDTO}` — 当其它模块需要调用本模块时, 定义 `XxxApi` interface (普通 Java interface, 跑 Spring DI; **不是** Feign / RPC) + 配套 DTO. 不需要跨模块调用就不建这个包
- `nook-module-<name>-server` — 实现 + 私有内部. 内含:
  - `controller/<feature>/{XxxController.java, vo/*}`
  - `service/{XxxService.java, impl/XxxServiceImpl.java}` — 本模块内部 service 接口
  - `api/<feature>/XxxApiImpl.java` — 若 -api 有 XxxApi 接口, 这里写实现 (跟接口同包, 跨 jar split-package, 参考 yudao-cloud)
  - `dal/{dataobject/XxxDO.java, mysql/mapper/XxxMapper.java}`
  - `convert/XxxConvert.java`
  - `framework/<feature>/*` — 模块私有的 Spring config / arg-resolver / interceptor 等
  - `validator/XxxValidator.java` — 含校验逻辑的 Service 必须配对一个; 简单读 / 仅透传的 Service 可省 (见 §10)

**禁止**:
- 跨模块 import `nook-module-<a>-server` 包内任何类
- 在 -api 模块放 Spring beans (`@Service` / `@Component` / `@Repository`) 或注解处理逻辑

**允许**:
- 模块 A 的 -server 依赖模块 B 的 -api (拿 Api 接口 + DTO 走 Spring DI)
- 任意模块依赖 `nook-common` / `nook-framework`

参考: `nook-module-agent` 的 `-api` / `-server` 拆分.

### 关联数据拼接 (跨模块 / 聚合视图: Service 经 Convert 直接返 VO)

跨模块 Api 调用 / 多源聚合属**业务编排, 放 Service**: Service 查主数据 → Convert 转入参 → 调跨模块 Api → Convert 把结果拼成 **VO** 返回; Controller 纯转发. **禁止**用 carrier record (如 `record PlanPage(page, capMap)`) 把 DO + 数据塞回 Controller —— 视图就是 VO, Service 直接返 VO.

```java
// Service: 查 + 调跨模块 Api + Convert 拼 VO, 直接返 VO (该视图无单一 DO 对应, 属 §8 例外)
public PageResult<SomeRespVO> getSomePage(SomePageReqVO req) {
    PageResult<SomeDO> plans = somePageQuery(req);
    List<XxxSpecDTO> specs = SomeConvert.INSTANCE.toSpecs(plans.getRecords());   // 入参转换抽独立一行, 不内联进 Api 调用
    Map<String, XxxDTO> capMap = xxxApi.batchLoad(specs);
    return SomeConvert.INSTANCE.convertPage(plans, capMap);                       // 出参拼 VO
}

// Controller: 纯转发
return Result.ok(someService.getSomePage(req));
```

- 此为 §1「Service 返回 DO, 不构建 VO」的**例外**, 仅限"无单一 DO 对应"的跨模块 / 聚合视图 (跟 §8 snapshot 例外同源); 普通单表 CRUD 仍 Service 返 DO + Controller 调 Convert.
- 入参转换 (`toSpecs`) 抽成**独立一行**, 不要内联嵌进 Api 调用 (可读性).
- Convert 只接纯数据 Map / DTO 拼 VO, **禁止**注入 Service / Api.

---

## 2. 集合与判空工具

**判空一律用 Hutool 工具类, 禁止手写 `== null` / `.isEmpty()` 拼接**: 集合 / 字符串 / 对象 / Map / 数组判空走 `CollUtil` / `StrUtil` / `ObjectUtil` / `MapUtil` / `ArrayUtil` (`cn.hutool.core.*`); 字段提取 / Map 构建 / 多集合判空走项目 `CollectionUtils`.

| 需求 | 写法 |
|---|---|
| 提取字段为 Set (去重) | `CollectionUtils.convertSet(list, SomeDO::getField)` |
| 提取字段为 List (过滤 null) | `CollectionUtils.convertList(list, SomeDO::getField)` |
| 构建 ID → DO 的 Map | `CollectionUtils.convertMap(list, SomeDO::getId)` |
| 多集合判空 | `CollectionUtils.isAnyEmpty(c1, c2, ...)` |
| 集合判空 | `CollUtil.isEmpty(list)` / `CollUtil.isNotEmpty(list)` |
| 字符串判空 | `StrUtil.isEmpty(s)` / `StrUtil.isNotBlank(s)` |
| 对象判空 | `ObjectUtil.isNull(obj)` / `ObjectUtil.isNotNull(obj)` |
| 数组判空 | `ArrayUtil.isEmpty(arr)` |
| Map 判空 | `MapUtil.isEmpty(map)` |
| 空分页 | `PageResult.empty()`, 禁止 `new PageResult<>(emptyList(), 0L)` |
| 对象比较 | `ObjectUtil.equal(a, b)` |

**禁止**手写 stream 做简单转换 (`list.stream().map(...).collect(...)` 单层映射).
**禁止**手写 `s == null || s.isEmpty()` / `list == null || list.isEmpty()`.

工具类位置: `com.nook.common.utils.collection.CollectionUtils`, `com.nook.common.utils.object.BeanUtils`, Hutool `cn.hutool.core.util.*`.

---

## 3. 主键与时间字段

### 实体命名: `XxxDO`

DB 实体类**强制**以 `DO` 后缀命名 (Data Object), 放 `com.nook.biz.<module>.dal.dataobject.<feature>` 包下. **禁止** `XxxEntity` / `XxxPO` / 裸 `Xxx` 命名. 基类 `BaseEntity` 是框架名, 不影响子类后缀.

### 主键: CHAR(32) UUID

```java
public class SomeDO extends BaseEntity {
    // BaseEntity 已经声明: @TableId(value = "id", type = IdType.ASSIGN_UUID) String id;
    // 子类**不要**再声明 id 字段
}
```

DDL:
```sql
id CHAR(32) NOT NULL PRIMARY KEY COMMENT '主键ID'
```

### 时间字段

`BaseEntity` 已包含 `createdAt` / `updatedAt` (`@TableField(value = "created_at", fill = FieldFill.INSERT)` / `INSERT_UPDATE`). `MetaObjectHandlerImpl` 在 INSERT / UPDATE 时自动填充. 子类**不要**再声明.

DDL:
```sql
created_at DATETIME NOT NULL COMMENT '创建时间',
updated_at DATETIME NOT NULL COMMENT '更新时间'
```

### Wrapper 更新绕过 fill

`Wrappers.lambdaUpdate().set(...).eq(...)` 这种写法**不会**触发 `MetaObjectHandlerImpl` 的 INSERT_UPDATE fill. 在 Mapper 的 default 更新方法里**必须显式** `.set(SomeDO::getUpdatedAt, LocalDateTime.now())`. 参考 `SystemUserMapper.updateLastLogin`.

---

## 4. 软删除与物理删除

`BaseEntity` 的子类一般会自带 `deleted` 字段 (`@TableLogic Integer deleted`). MP 拦截器自动给 `selectXxx` / `deleteXxx` 加 `WHERE deleted = 0`.

**"先删后增 + 唯一键" 场景必须物理删除**, 否则旧记录占用唯一键, INSERT 报 `Duplicate entry`.

**物理删除一律写 XML, 不用 `@Delete` 注解**: 注解形式可能绕过 `@TableLogic` 拦截器链路. 方法名以 `physicalDelete` 开头.

```java
// Mapper 接口
int physicalDeleteByXxx(@Param("xxx") String xxx);
```

```xml
<!-- resources/mapper/xxx/XxxMapper.xml -->
<delete id="physicalDeleteByXxx">
    DELETE FROM xxx WHERE xxx_field = #{xxx}
</delete>
```

---

## 5. Mapper 与 SQL

### 数据库结构变更 (DDL / 数据迁移)

**禁止**自作主张在仓库里生成 `script/sql/migration/*.sql` 等本地迁移脚本.

**正确流程**:
1. 先跟用户说明本次结构变更需要执行什么 SQL (DDL + 数据迁移 + 字典清理等)
2. 用户确认后通过 **MCP 数据库工具** (`mcp__my-database__execute_query`) 直接执行 → 校验最终状态
3. 仓库里只保留主表结构定义 (后续如有 `script/sql/nook.sql` 主文件再考虑), 不留迁移脚本

理由: 迁移脚本沉淀在仓库会被误以为是部署流程的一部分; 实际部署用 MCP 同步生产环境, 本地 SQL 文件反而是噪音.

### DDL 约束 (强制)

**禁止**:
- **外键约束** (`FOREIGN KEY ... REFERENCES ...`) — DDL 里不写 FK; 跨表引用关系由代码层 Validator (`validateXxxExists`) 校验
- **DEFAULT 默认值** — DB 列**不**写 `DEFAULT 'xxx' / DEFAULT 0` 等; 新建时由 ServiceImpl 显式赋值

**允许 / 推荐**:
- **NOT NULL** 约束 — 非空跟 DEFAULT 是两回事, **必须分清**:
  - `NOT NULL` ✅ 允许 — 强制业务必填的字段语义
  - `DEFAULT 'x'` ❌ 禁止 — 省略时的兜底值
  - NOT NULL 字段在 ServiceImpl 新建路径**必须**显式赋值, 漏赋会直接报错, 而不是被 DEFAULT 写出意外的 0 / 空串 / 错误状态
- **索引** (`INDEX` / `PRIMARY KEY`) — 查询性能
- **唯一索引** (`UNIQUE INDEX`) — 业务唯一性走"双重校验":
  - DB 层 `UNIQUE INDEX` 兜底防并发 race (Validator 查完到 INSERT 中间有时间窗)
  - 代码层 `XxxValidator.validateXxxUnique` 给用户可读错误码 (e.g. `SERVER_NAME_DUPLICATE`)
  - **两者必须都有**, 不能省任一侧

理由:
- 外键: 跨服务部署 / 分库分表 / 数据迁移时 FK 成阻碍; 跨表存在性校验放 Validator 更显式 (e.g. `someValidator.validateXxxExists`)
- DEFAULT: DB 默认值散落 DDL, 跟代码逻辑双重事实源, 改值时容易漏 DB 一侧; 默认值统一在 ServiceImpl 一次写, "默认值是什么"代码即文档
- 唯一性双重: 只有 UNIQUE INDEX 没 Validator → 用户看到的是 SQL 异常 (难定位); 只有 Validator 没 UNIQUE INDEX → 并发场景两个请求都通过校验后 INSERT, 落两条重复

### 写法分级

| 复杂度 | 写法 |
|---|---|
| MyBatis-Plus 内置 | `selectById` / `insert` / `selectList` 等 |
| 动态条件单表 | Mapper `default` 方法 + `Wrappers.lambdaQuery()`, 用 `eq(条件, lambda, 值)` 三参重载做 IfPresent |
| 物理删除 | XML (见 §4) |
| 多表 JOIN / 子查询 | XML 映射文件 (`resources/mapper/<module>/*.xml`) |

### Mapper 写法 (用 MP 原生 BaseMapper)

```java
@Mapper
public interface SomeMapper extends BaseMapper<SomeDO> {

    /** 按某字段精确查找; 找不到返回 null. */
    default SomeDO selectByField(String field) {
        return selectOne(Wrappers.<SomeDO>lambdaQuery()
                .eq(SomeDO::getField, field));
    }

    /** 某字段是否存在. */
    default boolean existsByField(String field) {
        return exists(Wrappers.<SomeDO>lambdaQuery()
                .eq(SomeDO::getField, field));
    }

    /** 排除指定 id 的存在性检查 (更新时查重). */
    default boolean existsByFieldExcludingId(String field, String excludeId) {
        return exists(Wrappers.<SomeDO>lambdaQuery()
                .eq(SomeDO::getField, field)
                .ne(SomeDO::getId, excludeId));
    }

    /** 列表分页. */
    default IPage<SomeDO> selectPageByQuery(IPage<SomeDO> page, SomePageReqVO reqVO) {
        return selectPage(page, Wrappers.<SomeDO>lambdaQuery()
                .eq(ObjectUtil.isNotNull(reqVO.getStatus()), SomeDO::getStatus, reqVO.getStatus())
                .and(StrUtil.isNotBlank(reqVO.getKeyword()), q -> q
                        .like(SomeDO::getName, reqVO.getKeyword())
                        .or().like(SomeDO::getEmail, reqVO.getKeyword()))
                .orderByDesc(SomeDO::getCreatedAt));
    }

    /** 部分字段更新; 用 Wrapper 更新时必须显式 set updated_at. */
    default int updateXxx(String id, String newValue) {
        return update(null, Wrappers.<SomeDO>lambdaUpdate()
                .set(SomeDO::getXxx, newValue)
                .set(SomeDO::getUpdatedAt, LocalDateTime.now())
                .eq(SomeDO::getId, id));
    }
}
```

### 禁止

- SQL 注解 (`@Select` / `@Update` / `@Insert` / `@Delete`), 全部走 `default` 方法或 XML
- Service 中拼接 SQL 字符串 (注入风险)
- Service 中直接构造 `Wrappers`, 应封装到 Mapper `default` 方法 (Service 不感知 Wrapper)

---

## 6. 异常与错误码

### 错误码定义 (Nook 用 `ErrorCode` 接口)

每模块一个 `XxxErrorCode` 类 (枚举或常量类), 实现 `com.nook.common.web.error.ErrorCode` 接口.

```java
// nook-common 已定义的全局错误码 (引用即可)
CommonErrorCode.UNAUTHORIZED         // 未登录
CommonErrorCode.FORBIDDEN            // 无权限
CommonErrorCode.PARAM_INVALID        // 参数错误
CommonErrorCode.INTERNAL_ERROR       // 服务器异常
```

```java
// 业务模块错误码 (int 段位 + %s 格式化占位符)
@Getter
@RequiredArgsConstructor
public enum SomeErrorCode implements ErrorCode {

    SOME_NOT_FOUND(4001, "XX不存在"),
    SOME_DISABLED(4002, "XX已禁用, 请联系管理员"),
    NAME_EXISTS(4003, "名称 %s 已存在"),
    ;

    private final int code;
    private final String message;
}
```

错误码段位 (int):
- `1xxx`: 通用 (`CommonErrorCode`)
- `2xxx`: 后台 system 模块 (`SystemErrorCode`)
- `3xxx`: 会员 member 模块 (`MemberErrorCode`)
- `4xxx`: 资源 resource 模块
- `5xxx`: 订阅 sub 模块
- ... 各模块按千位段位划分

消息模板用 `%s` 占位 (`String.format` 兼容), 不是 `{}`. 抛异常: `throw new BusinessException(SomeErrorCode.NAME_EXISTS, name);`.

### 抛出异常

```java
import com.nook.common.web.exception.BusinessException;

throw new BusinessException(SomeErrorCode.SOME_NOT_FOUND);
throw new BusinessException(SomeErrorCode.NAME_EXISTS, name);
```

全局异常处理器 (在 `nook-framework` 已配) 会把 `BusinessException` 转 `Result.fail(code, msg)` 返回前端.

---

## 7. Controller

### 路径风格 (强制)

**两条铁律**:

1. **禁止** `@PathVariable` 路径参数 (`/{id}`, `/{id}/socks5` 等). 所有 id / serverId 等标识统一走 `@RequestParam` query string.
2. **所有 endpoint** 必须 `<动词>-<名词>[-<修饰>]` 形式; 禁止纯名词 (`/capacity`) 或纯动词 (`/create`).

| ✅ 推荐 | ❌ 禁止 |
|---|---|
| `GET /get-server?id=...` | `GET /get` 或 `GET /{id}` |
| `PUT /update-capacity?id=...` | `PUT /capacity` 或 `PUT /{id}/capacity` |
| `POST /create-frontline` | `POST /create` |
| `DELETE /delete-server?id=...` | `DELETE /delete` |
| `POST /transition-lifecycle?id=...` | `POST /lifecycle` |
| `GET /page-landing` | `GET /page` |
| `GET /list-region` | `GET /list` |
| `GET /get-summary` | `GET /summary` |
| `POST /upgrade-agent` | `POST /upgrade` |

理由: 路径自描述, admin REST 与 agent 协议端点都能从 url 直接读出意图; 前端 url 拼接统一; OpenAPI 推断准确.

**例外 (保留惯例)**:
- 鉴权端点: `/login` `/logout` `/register` `/me` (语义自明, 行业惯例)
- Agent 协议端点 `/api/agent/*`: `/heartbeat` `/tasks` `/task-result` 等 (agent → backend 的 push 协议, 不是 REST CRUD)
- 订阅 / 分享类公开 URL (如 `/portal/sub/{token}`): 供外部客户端 (v2rayN / clash 等) 直接导入, 路径式 token 是行业惯例, 改 query 参数会破坏已分发链接; 允许 `@PathVariable`

### 类与方法

```java
@RestController
@RequestMapping("/admin/<module>/<feature>")     // admin 路径; customer 路径前缀 /customer
@RequiredArgsConstructor
@Validated
public class SomeController {

    private final SomeService someService;

    @PostMapping("/create-some")
    public Result<String> create(@RequestBody @Valid SomeCreateReqVO reqVO) {
        return Result.ok(someService.create(reqVO).getId());
    }

    @PutMapping("/update-some")
    public Result<Void> update(@RequestParam("id") String id, @RequestBody @Valid SomeUpdateReqVO reqVO) {
        someService.update(id, reqVO);
        return Result.ok();
    }

    @GetMapping("/page-some")
    public Result<PageResult<SomeRespVO>> page(@Valid SomePageReqVO reqVO) {
        PageResult<SomeDO> page = someService.page(reqVO);
        return Result.ok(PageResult.of(page.getTotal(),
                SomeConvert.INSTANCE.convertList(page.getList())));
    }

    @GetMapping("/get-some")
    public Result<SomeRespVO> get(@RequestParam("id") String id) {
        return Result.ok(SomeConvert.INSTANCE.convert(someService.findById(id)));
    }

    @DeleteMapping("/delete-some")
    public Result<Void> delete(@RequestParam("id") String id) {
        someService.delete(id, StpSystemUtil.getLoginIdAsString());
        return Result.ok();
    }
}
```

### 鉴权

通过 sa-token 拦截器在 Spring 配置层拦截 (见 §10).

| 路径前缀 | 拦截器 | StpLogic |
|---|---|---|
| `/admin/**` | `StpInterceptor` (`StpSystemUtil.stpLogic()`) | system |
| `/customer/**` | `StpInterceptor` (`StpMemberUtil.stpLogic()`) | member |
| `/admin/system/auth/login`, `/customer/<module>/auth/register`, `/customer/<module>/auth/login` | 放行 | — |

**禁止**在 Controller 方法上写 `StpSystemUtil.checkLogin()` 这种零散校验, 统一走拦截器.

### 依赖注入与返回

- 用 `@RequiredArgsConstructor` + `private final SomeService someService` 构造注入. **禁止** `@Autowired` / `@Resource`.
- 字段声明用 import 后的短名, **禁止**全限定名.
- 返回 `Result.ok(SomeConvert.INSTANCE.convertList(list))`, **禁止**手动 `new RespVO(...)` 逐字段赋值.

---

## 8. Convert

```java
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface SomeConvert {

    SomeConvert INSTANCE = Mappers.getMapper(SomeConvert.class);

    SomeRespVO convert(SomeDO entity);

    List<SomeRespVO> convertList(List<SomeDO> list);

    SomeDO convert(SomeCreateReqVO vo);

    /** 提取 memberId 集合, 供 Controller 批量查关联. */
    default Set<String> extractMemberIds(List<SomeDO> list) {
        return CollectionUtils.convertSet(list, SomeDO::getMemberUserId);
    }

    /** 与会员名拼接. */
    default List<SomeRespVO> convertListWithMemberName(List<SomeDO> list, Map<String, String> memberNameMap) {
        List<SomeRespVO> voList = convertList(list);
        for (SomeRespVO vo : voList) {
            vo.setMemberName(memberNameMap.get(vo.getMemberUserId()));
        }
        return voList;
    }
}
```

`default` 方法只接收纯数据 Map / List, **禁止**注入或传入 Service.

### Convert 调用方边界 (强制)

Convert 本质是**跨层数据格式翻译**. 按输入类型决定谁调用 Convert:

| Convert 输入 | 调用方 | 理由 |
|---|---|---|
| **业务实体 DO** (`dal/dataobject/*DO`) | **Controller** | 标准三层: Controller 拿 Service 返的 DO → Convert → VO |
| **基础设施 Snapshot** (`framework/**/snapshot/*` 等 framework 层数据载体) | **Service** | snapshot 是 service 从 framework 拉的中间数据; Controller 不该 import framework 内部结构, service 是唯一能桥接基础设施层 ↔ 业务 VO 的位置 |
| **跨模块 / 聚合视图** (无单一 DO 对应) | **Service** | Service 查 + 调跨模块 Api + Convert 拼 VO **直接返 VO** (见 §1); Controller 纯转发, 不碰其他模块 Api |

**核心判定**: Convert 的输入来自哪一层, 就由那一层的调用者触发翻译.

**反例**:

```java
// ❌ 错 A: Controller 直接 import framework snapshot 调 Convert
@GetMapping("/get-some")
Result<SomeRespVO> getSome(@RequestParam("id") String id) {
    SomeSnapshot snap = someService.probe(id);                 // service 漏出 framework 类
    return Result.ok(SomeConvert.INSTANCE.convert(snap));      // controller 拼 framework → VO
}
// → 破坏分层: framework 层暴露到 web 层
// → 修法: service 内拼好 VO 返出, controller 不接触 snapshot
```

```java
// ❌ 错 B: Service 接口返 DO, Impl 提前调 Convert 拼 VO
public FooDO findById(String id) {
    FooDO doObj = mapper.selectById(id);
    FooRespVO vo = FooConvert.INSTANCE.convert(doObj);    // 多此一举: 接口签名又只返 DO
    return doObj;
}
// → 职责混乱
// → 修法: 要么接口改返 VO (确认是 framework → VO 例外场景), 要么 Convert 调用挪 Controller
```

**正确示例 (Service 调 Convert, snapshot → VO)**:

```java
// Service 接口: 远端 SSH 探测返复合 VO, 无对应持久化 DO
public interface SomeOpsService {
    SomeStatusRespVO getStatus(String serverId);
}

// Service Impl: 拉 framework snapshot → Convert 翻译 → 返 VO
@Override
public SomeStatusRespVO getStatus(String serverId) {
    FooSnapshot foo = someProbe.readFoo(...);
    BarSnapshot bar = someProbe.readBar(...);
    return SomeOpsConvert.INSTANCE.toStatusRespVO(foo, bar, ...);
}
```

### Convert 注释规则 (§14 例外)

Convert 是纯字段映射, **方法名 + `@Mapping` 注解已自解释**, 写注释属噪声. 强制规则:

- **不写类级 javadoc** (§14 "所有类必须多行 + @author" 的明确例外)
- **不写方法级 javadoc** (§14 "Service 接口动作短语" 同理不适用)
- **不写 `@Mapping` 解释性行内注释** (`@Mapping(target=..., source=...)` 本身就是声明)
- **仅在非平凡字段映射** (e.g. 跨类型转换 / `expression=...` / `qualifiedBy`) 时, 可在该 `@Mapping` 上一行加单行注释解释为什么需要特殊处理

```java
// ✅ 正确写法
@Mapper
public interface SomeOpsConvert {
    SomeOpsConvert INSTANCE = Mappers.getMapper(SomeOpsConvert.class);

    FooRespVO toFooVO(FooSnapshot snapshot);

    @Mapping(target = "barStatus", source = "bar")
    SomeStatusRespVO toStatusRespVO(SysSnapshot sys, String bar, FooSnapshot foo);
}

// ❌ 错: 类/方法 javadoc 都是字段名复述
/**
 * XX 节点 Convert
 * @author nook
 */
@Mapper
public interface SomeOpsConvert {
    /** snapshot → VO */
    FooRespVO toFooVO(FooSnapshot snapshot);
}
```

---

## 9. VO 命名

| 后缀 | 用途 | HTTP |
|---|---|---|
| `CreateReqVO` | 创建 | POST `/create` |
| `UpdateReqVO` | 更新 | PUT `/{id}` |
| `PageReqVO` | 分页查询 (含 `pageNo` / `pageSize`) | GET `/page` |
| `RespVO` | 标准响应 | 出参 |
| `SimpleRespVO` | 下拉等精简响应 | 出参 |

> Create / Update 用独立 VO, **不**合并成单个 `SaveReqVO`. 沿用 Nook 现状.

**禁止** `DTO` / `Result` / `Form` 等其他后缀.

### 字段校验

```java
@Schema(description = "管理后台 - 业务表创建 Request VO")
@Data
public class SomeCreateReqVO {

    @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "名称不能为空")
    @Size(max = 100, message = "名称长度不能超过 100")
    private String name;

    @Schema(description = "邮箱", example = "user@example.com")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    @Schema(description = "状态: 1=正常 2=禁用")
    private Integer status;

    @Schema(description = "嵌套对象列表")
    @Valid                              // 嵌套必须加, 否则不触发内部校验
    private List<NestedReqVO> items;
}
```

@Schema 注解非强制 (起步阶段未集成 Swagger), 但写上方便后续接入.

### 允许的 Bean Validation 注解 (结构性 / 边界值校验)

| 注解 | 用途 |
|---|---|
| `@NotBlank` / `@NotNull` | 非空 (NotBlank 拒空串, NotNull 仅拒 null) |
| `@Size(max=N)` | 字符串 / 集合长度 |
| `@Min(N)` / `@Max(N)` | 数值边界 |
| `@Email` | 邮箱格式 |
| `@Pattern(regexp=...)` | **简单**字符集 / 大小写格式 (e.g. 区域码须大写英文) |
| `@Valid` | 嵌套对象 (必须加, 否则不触发内部校验) |

### 禁止用注解, 必须写到对应 `XxxValidator` 中

| 校验场景 | 错误示例 | 正确做法 |
|---|---|---|
| 枚举取值范围 | `@Pattern(regexp="A|B|C")` | `validateXxx()` 内走 `Enum.fromState() != null` |
| 业务唯一性 | 无注解可表达 | `validateXxxUnique` 查 mapper `existsByXxx[ExcludingId]` |
| 存在性 (跨表引用) | 无注解可表达 (没 FK) | `validateXxxExists` 查 mapper |
| 跨字段条件 (A 非空时 B 必填) | `@AssertTrue` SpEL 类 | Validator 内 if-then 抛 `BusinessException` |
| 复杂格式 / 业务规则 | 正则堆叠 | Validator 内显式判断 |

理由:
- 枚举注解列举值: 新增枚举值时必须同步改 VO `@Pattern` 字符串, 容易漏改 → 走枚举类 `fromState` / `matches` 天然同步
- 业务校验离 mapper / service / 错误码上下文近, 错误消息更可控
- Validator 集中后单测 / 跨入口复用更顺 (Create + Update 共用一套聚合 validator 方法)

---

## 10. Service

### 接口 + Impl 分离

```java
public interface SomeService {
    SomeDO create(SomeCreateReqVO reqVO);
    void update(String id, SomeUpdateReqVO reqVO);
    void delete(String id, String operatorId);
    SomeDO findById(String id);
    PageResult<SomeDO> page(SomePageReqVO reqVO);
}

@Service
@RequiredArgsConstructor
public class SomeServiceImpl implements SomeService {

    private final SomeMapper someMapper;
    private final SomeValidator someValidator;
    // ...

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SomeDO create(SomeCreateReqVO reqVO) {
        someValidator.validateForCreateOrUpdate(null, reqVO.getName(), reqVO.getEmail());

        SomeDO entity = BeanUtils.toBean(reqVO, SomeDO.class);
        someMapper.insert(entity);
        return entity;
    }
}
```

### 事务

- **多条 / 多表写入**必须 `@Transactional(rollbackFor = Exception.class)` (默认只回滚 `RuntimeException`)
- **单条 DML** (一次 insert / update / delete) 本身原子, **不必**加 `@Transactional`; 即便前面有校验读、后面跟外部调用, 只要落库是单条写就不需要事务
- **读操作**不加事务
- 跨多表写入 + 外部调用 (HTTP / RPC): 事务内只做 DB, 外部调用拆事务外 (见 [03-业务核心 §4.7](../subscription-system-v3/03-业务核心-算法与流程.md) provision 模式)

### 异步

`@Async` 必须放在独立 `XxxAsyncHelper` 组件, **禁止** Service 自注入 (AOP 代理失效):

```java
@Component
@RequiredArgsConstructor
public class SomeAsyncHelper {

    private final SomeServiceImpl someService;

    @Async("someExecutor")
    public void doAsync(...) {
        try { someService.doSomething(...); }
        catch (Exception e) { log.error("[doAsync] 执行异常", e); }
    }
}
```

### Validator (强制: 含校验逻辑的 Service 必须配对独立 `XxxValidator` 类)

校验逻辑 (存在性 / 唯一性 / 业务前置 / 参数语义) **统一集中**到独立的 `XxxValidator` 类, Service 通过构造注入调用. 命名与目标 Service 一一对应 (`SomeService` ↔ `SomeValidator`); 简单读 / 仅透传的 Service 没有校验逻辑可省 Validator.

```java
@Component
@RequiredArgsConstructor
public class SomeValidator {

    private final SomeMapper someMapper;

    /**
     * 聚合入口: Create/Update 共用; id=null 表示 Create.
     *
     * @param id    当前行 id, null 表示 Create
     * @param name  待校验名称
     * @param email 待校验邮箱
     * @return Update 路径返回当前 entity (Create 路径返 null)
     */
    public SomeDO validateForCreateOrUpdate(String id, String name, String email) {
        SomeDO existing = validateExists(id);
        validateNameUnique(id, name);
        validateEmailUnique(id, email);
        return existing;
    }

    /** id=null 直接返 null (Create 路径); 找不到抛 NOT_FOUND. */
    public SomeDO validateExists(String id) {
        if (id == null) return null;
        SomeDO e = someMapper.selectById(id);
        if (ObjectUtil.isNull(e)) throw new BusinessException(SomeErrorCode.NOT_FOUND);
        return e;
    }

    /** 唯一性校验; id 非空时排除自身 (Update 不冲突自己). */
    public void validateNameUnique(String id, String name) {
        if (StrUtil.isBlank(name)) return;
        boolean dup = id == null
                ? someMapper.existsByName(name)
                : someMapper.existsByNameExcludingId(name, id);
        if (dup) throw new BusinessException(SomeErrorCode.NAME_EXISTS, name);
    }
}
```

**Service 端调用**:

```java
@Service
@RequiredArgsConstructor
public class SomeServiceImpl implements SomeService {

    private final SomeMapper someMapper;
    private final SomeValidator someValidator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SomeDO create(SomeCreateReqVO reqVO) {
        someValidator.validateForCreateOrUpdate(null, reqVO.getName(), reqVO.getEmail());
        SomeDO entity = BeanUtils.toBean(reqVO, SomeDO.class);
        someMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SomeUpdateReqVO reqVO) {
        someValidator.validateForCreateOrUpdate(reqVO.getId(), reqVO.getName(), reqVO.getEmail());
        // ...
    }
}
```

**约定**:
- 位置: `nook-module-<name>-server/.../validator/XxxValidator.java`
- 注解: `@Component` + `@RequiredArgsConstructor` (跟全局注入规范一致, 禁 `@Resource`)
- 方法命名: `validateXxxExists` / `validateXxxUnique` / `validateXxxForCreateOrUpdate` / `validateXxxValue` (语义校验) 等
- 跨 Service 复用: 同模块多个 Service 引用同一个 Validator 即可; **跨模块**走 `nook-module-<name>-api` 的 `XxxApi` (见 §1), 不要让其他模块直接注入 Validator
- 参数校验注解能搞定的 (如 `@NotBlank`, `@Email`) 仍由 `@Valid` 在 Controller 层完成, Validator 只接业务层的存在性 / 唯一性 / 跨字段约束

**职责边界 (跟 §9 校验清单呼应)**:
凡是涉及 **枚举取值范围 / 业务唯一性 / 存在性 / 跨字段条件 / 复杂格式** 的校验必须在 Validator 里, **不**在 VO 注解里. VO 只负责"结构性 + 边界值"校验 (`@NotBlank/@Size/@Min/@Max/@Email/@Valid`).

**唯一性校验配 UNIQUE INDEX**:
`validateXxxUnique` 只是给用户友好错误码 (`SERVER_NAME_DUPLICATE`), 不能替代 DB 层 `UNIQUE INDEX`. 并发场景两个请求可能同时通过 Validator 后 INSERT, 落两条重复. 两者必须都有 (见 §5 DDL 约束).

参考: `SystemUserValidator`, `nook-module-agent` 各 Validator.

---

## 11. 模块结构 (maven 双子模块)

每个业务模块 = 1 个父 pom (`packaging=pom`) + 2 个子 maven 模块 (`-api` + `-server`).

```
nook-module-<name>/
├── pom.xml                         # 父 pom, packaging=pom, 只声明 modules
├── nook-module-<name>-api/
│   ├── pom.xml                     # 极简, 只 jakarta.validation 等编译期依赖
│   └── src/main/java/com/nook/biz/<name>/
│       ├── enums/<feature>/        # 跨模块共享的枚举 (5xxx 状态码 / 业务状态机 / 角色 / 类型)
│       │   └── XxxEnum.java
│       └── api/<feature>/          # 若需要被其它模块调用, 放 facade Api 接口
│           ├── XxxApi.java         # 普通 Java interface (非 Feign)
│           └── dto/
│               ├── XxxRespDTO.java
│               └── XxxSaveReqDTO.java
└── nook-module-<name>-server/
    ├── pom.xml                     # 依赖 -api + nook-spring-boot-starter-* (web/security/mybatis 等按需) + 跨模块的 -api
    └── src/main/java/com/nook/biz/<name>/
        ├── api/                        # XxxApi 接口的实现, 跟接口同包 (跨 jar split-package)
        │   └── <feature>/
        │       └── XxxApiImpl.java     # @Service, implements XxxApi
        ├── controller/
        │   ├── <feature>/              # 每个 feature 一个子包
        │   │   ├── XxxController.java  # admin/portal 在路径前缀区分
        │   │   └── vo/
        │   │       ├── XxxCreateReqVO.java
        │   │       ├── XxxUpdateReqVO.java
        │   │       ├── XxxPageReqVO.java
        │   │       └── XxxRespVO.java
        │   └── admin/<feature>/        # admin 后台专用 (可选, 路径在类上区分)
        ├── service/
        │   ├── XxxService.java         # 模块内部 service 接口
        │   ├── impl/
        │   │   ├── XxxServiceImpl.java
        │   │   └── XxxApiImpl.java     # 实现 -api 的 XxxApi (若有)
        ├── dal/
        │   ├── dataobject/<feature>/
        │   │   └── XxxDO.java          # MP 实体, 后缀 DO
        │   └── mysql/mapper/
        │       └── XxxMapper.java
        ├── convert/<feature>/
        │   └── XxxConvert.java
        ├── framework/<feature>/        # 模块私有 config / arg-resolver / interceptor
        │   └── XxxConfig.java
        └── validator/                  # 跟 Service 一一对应; 仅简单读 / 透传 Service 可省 (见 §10)
            └── XxxValidator.java
```

参考: `nook-module-agent` (已按此结构拆); `nook-module-system` / `nook-module-member` / `nook-module-node` 待逐步拆.

> 实体后缀: 新代码统一用 `XxxDO` (跟 mybatis-plus 习惯一致). 旧代码 (`SystemUser` / `XrayClient` 等) 沿用, 重构时顺手改。

---

## 12. 枚举

代码逻辑用枚举 (Nook 起步不引字典).

### 强制: 状态 / 类型 / 分类字段必须有枚举类

凡是字段语义是"有限取值集合" (生命周期 / 占用状态 / 角色 / 类型 / 部署模式 / 装机模式 / 限流状态 / 重置策略 / 协议 等), DB 列存 `code`, Java 必须配套一个 Enum 类:

- 位置: `nook-module-<name>-api/.../api/enums/<Domain><Field>Enum.java` (跨模块共享时) 或 `-server/.../api/enums/` (仅内部用); 命名 `XxxEnum`.
- 严禁 Service / Mapper / Controller 内出现散落的 `"AVAILABLE"` / `"LIVE"` / `1` / `2` 等魔法字面量. 一律走 `Enum.values()` / `Enum.fromState(...)` / `Enum.getState()` / `Enum.matches(state)`.
- DO 字段保持原始类型 (`Integer code` / `String state`), 不要直接持有 Enum (MyBatis-Plus typeHandler 不引), 但 javadoc 必须 `{@link XxxEnum}` 指向枚举类:

```java
public class SomeDO extends BaseEntity {
    /** 装机生命周期 {@link SomeLifecycleEnum} */
    private String lifecycleState;

    /** 角色 {@link SomeRoleEnum} */
    private String roleType;
}
```

### 强制: 枚举骨架

整型 code:

```java
@Getter
@AllArgsConstructor
public enum AccountStatusEnum {

    NORMAL(1, "正常"),
    DISABLED(2, "禁用");

    private final Integer code;
    private final String name;

    public static AccountStatusEnum fromCode(Integer code) {
        if (code == null) return null;
        for (AccountStatusEnum e : values()) if (e.code.equals(code)) return e;
        return null;
    }

    public boolean isNormal() { return this == NORMAL; }
}
```

字符串 state (允许小写 / 大写, 跟 DB 约束一致):

```java
@Getter
@AllArgsConstructor
public enum SomeStatusEnum {

    AVAILABLE("AVAILABLE", "可分配"),
    RESERVED("RESERVED", "预占中"),
    OCCUPIED("OCCUPIED", "已占用"),
    COOLING("COOLING", "冷却中"),
    ;

    public static final String[] ARRAYS = Arrays.stream(values())
            .map(SomeStatusEnum::getState).toArray(String[]::new);

    private final String state;
    private final String label;

    public static SomeStatusEnum fromState(String state) {
        if (state == null) return null;
        for (SomeStatusEnum e : values()) {
            if (e.state.equals(state)) return e;
        }
        return null;
    }

    public boolean matches(String state) { return this.state.equals(state); }
}
```

数据库存 `code` (整型) 或 `state` (字符串), 实体字段用对应原始类型. 比较一律 `Enum.matches(field)` / `Enum.fromXxx(field)`, **禁止** `"AVAILABLE".equals(do.getStatus())` 这种字面量比较.

### 强制: 不用全限定名

代码内引用类型一律走 `import`, **禁止**写 `java.util.Map<String, com.nook.biz.some.api.dto.SomeRespDTO>` 这种全限定名. 包内冲突时用别名或重命名变量, 不要靠全限定区分.

```java
// ❌ 错
public java.util.Map<String, com.nook.biz.some.dal.dataobject.SomeDO> getSomeMap() { ... }

// ✅ 对
import java.util.Map;
import com.nook.biz.some.dal.dataobject.SomeDO;
public Map<String, SomeDO> getSomeMap() { ... }
```

枚举值 javadoc 引用也是: `{@link SomeLifecycleEnum}` (不带包名). IDE 解析靠 import.

---

## 13. 日志

```java
@Slf4j
public class SomeServiceImpl { ... }

log.info("[login] 登录成功: userId={}, ip={}", userId, ip);
log.warn("[login] 登录失败: username={}, ip={}", username, ip);
log.error("[provisionUser] 调用 CF 失败: subId={}", subId, e);    // 异常对象放最后
```

格式: `[methodName] 描述: key={}, key={}`. **禁止**字符串拼接.

| 级别 | 场景 |
|---|---|
| ERROR | 影响业务 / 需人工介入 (e.g., 外部调用失败, 数据不一致) |
| WARN | 可恢复异常 / 重试 / 降级 / 用户操作错误 (如登录失败) |
| INFO | 关键业务节点 (注册, 登录, 订阅切换) |
| DEBUG | 调试用 (生产关闭) |

---

## 14. 注释规范

**原则: 代码自解释, 注释只解释"为什么".**

### 必须加

1. 多步骤业务流程 — 每步业务含义小标题 (**不带** `1./2./3.` 编号前缀)
2. 非显然决策 — 设计取舍 / 踩坑规避 / 安全考虑
3. 反直觉行为 — 说明原因

### 禁止加

- 复述代码字面意思 (`// 遍历字段列表`)
- 自解释变量的用途说明 (`// 用户 ID`)
- `// 1.` `// 2.` 等序号前缀
- 注释掉的废弃代码 (直接删, git 有历史)
- bug 调查史 / 版本踩坑 (属于 commit message, 不属于代码注释)
- **跨模块叙述**, 如"不下沉到 xxx 模块" — 属于设计文档不属于代码

### 类级 Javadoc (yudao 多行风格, 全系统统一)

跟 yudao / zqwl 对齐, **所有类** (包括 Controller / Service / ServiceImpl / Validator / Mapper / DO / VO / framework 基础设施 / Properties / Configuration / 注解 / Resolver / Helper 等) **必须**用多行格式 + `@author nook`. 主体**一行简述职责** (动词或名词短语, 无句号), 不展开实现细节. **禁止**单行 `/** ... */` 类级注释, **禁止**用 `<ul>/<ol>` 列举多分支细节, **禁止**在主体写 how / 踩坑史 / 跨模块叙述.

**例外**: **Convert 类不写任何 javadoc** (类/方法/`@Mapping` 都不写, 仅非平凡映射可加单行行内). 详见 §8 "Convert 注释规则".

```java
/**
 * XX Service 接口
 *
 * @author nook
 */
public interface SomeService { ... }

/**
 * XX Service 实现类
 *
 * @author nook
 */
@Service
@RequiredArgsConstructor
public class SomeServiceImpl implements SomeService { ... }

/**
 * XX 业务校验
 *
 * @author nook
 */
@Component
@RequiredArgsConstructor
public class SomeValidator { ... }

/**
 * 管理后台 - XX Controller
 *
 * @author nook
 */
@RestController
@RequestMapping("/admin/<module>/some")
public class SomeController { ... }

/**
 * XX DO
 *
 * @author nook
 */
@TableName("some_table")
public class SomeDO extends BaseEntity { ... }

/**
 * XX Mapper
 *
 * @author nook
 */
@Mapper
public interface SomeMapper extends BaseMapper<SomeDO> { ... }

/**
 * 管理后台 - XX创建 Request VO
 *
 * @author nook
 */
@Data
public class SomeCreateReqVO { ... }

/**
 * XX binary 解析
 *
 * @author nook
 */
@Component
public class SomeBinaryResolver { ... }

/**
 * XX 模块配置项
 *
 * @author nook
 */
@ConfigurationProperties(prefix = "nook.some")
public class SomeProperties { ... }
```

(Convert 类不写 javadoc, 见 §8, 故上表不含 Convert 示例)

#### 主体一行命名约定

| 类型 | 主体格式 |
|---|---|
| Service 接口 | `<业务名称> Service 接口` |
| ServiceImpl | `<业务名称> Service 实现类` |
| Validator | `<业务名称> 业务校验` |
| Controller (admin) | `管理后台 - <业务名称> Controller` |
| Controller (portal) | `客户端 - <业务名称> Controller` |
| Controller (Agent push / 内部) | `<业务名称> Controller` |
| DO | `<业务名称> DO` |
| Mapper | `<业务名称> Mapper` |
| Convert | — (不写 javadoc, 见 §8) |
| Request VO | `管理后台/客户端 - <业务名称><动作> Request VO` |
| Response VO | `管理后台/客户端 - <业务名称><场景> Response VO` |
| framework 基础设施 (Resolver / Factory / Properties / Configuration / 注解 / ArgumentResolver / Config 等) | `<职责短语>` (无固定后缀, 跟类名对应) |

#### 约定

- 第二行空, 然后 `@author nook` (统一作者名, **不要**用人名 / 邮箱)
- 跨模块 Api 接口实现 `XxxApiImpl` 按"Service 实现类"写
- 注解 `@interface` 也按这个格式写
- 单元测试类 (`XxxTest`) 按 "<业务名称> 单元测试" 写
- 主体里禁止写实现细节 / "用 SHA256 计算" / "跑 install/nook-agent.sh.tmpl" 这类 how; how 放方法/字段 javadoc 或行内注释

### 方法级 Javadoc

**默认用标准格式**(说明 + `@param` + `@return`, void 可省 `@return`).
**只有底层 + 优先级低**的方法可以单行: `private` helper / 简单透传 / `@Override` 平凡方法.

- **`@param`**: 写参数的业务含义 (`会员ID集合`), **禁止**写它对应哪张表哪个列 (`member_user.id 集合` 这种是噪声)
- **`@return`**: 直接写返回的内容或类型即可 (`分页列表` / `Map<String, String>`), **不展开**成整句描述; 返回空 / null 的边界说明放首行描述里, 不堆进 `@return`

#### Service 接口方法 (yudao 极简风格, 强制)

跟 yudao / zqwl 对齐, **首行只写动作短语**(动词 + 宾语, 无句号、无 how、无实现细节), `@param` / `@return` 用单个名词或极短短语. **不要**把"backend 拼 yaml → 写远端"、"CAS 标 PICKED 防并发"这类实现细节写进 javadoc; 实现细节属于方法体行内注释.

```java
/**
 * 创建XX
 *
 * @param reqVO 创建信息
 * @return 主键ID
 */
String createSome(@Valid SomeSaveReqVO reqVO);

/**
 * 获得XX分页列表
 *
 * @param reqVO 分页条件
 * @return 分页列表
 */
PageResult<SomeDO> getSomePage(SomePageReqVO reqVO);

/**
 * 修改密码
 *
 * @param id       主键ID
 * @param password 密码
 */
void updatePassword(String id, String password);

/**
 * 根据ID批量查询名称(key=ID, value=名称)
 *
 * @param ids ID集合
 * @return Map<String, String>
 */
Map<String, String> getNameMap(Collection<String> ids);
```

ServiceImpl 内的 `@Override` 方法**不重复写 javadoc** (跟接口 javadoc 一致, IDE 会继承).

#### Validator / 私有方法 javadoc

**Validator public 方法用完整标准 javadoc (描述 + `@param` / `@return`), 跟 Service 接口方法对齐, 不要写成单行** (Validator 是被多处复用的业务校验, 单行说不清). Service 内 private helper 可酌情更详细或单行.

```java
// ✅ Validator public 方法: 完整 javadoc, 描述用枚举 label 不写 raw code
/**
 * 校验套餐下是否存在 "生效中" 的订阅 (删套餐前置, 有则拒删)
 *
 * @param id 套餐ID
 */
public void validateNoActiveSub(String id) { ... }

// ❌ 单行 + 写 raw 值 ACTIVE
/** 删套餐前: 不能还有 ACTIVE 订阅. */
public void validateNoActiveSub(String id) { ... }
```

**描述状态 / 枚举取值用枚举的 `label` (人话), 不写 raw `code` / `value`**: 如 `生效中` 而非 `ACTIVE`, `已上架` 而非 `1` —— label 是给人读的、更准确, raw 值是给机器比的. 适用于 javadoc / 行内 / 字段注释.

### 字段级 Javadoc

**适用范围 (强制)**: 所有数据载体类的**每个字段都要有注释, 不留裸字段** —— 不只 `DO`, 还包括 `VO` / `DTO` / 基础设施封装的视图载体 (framework 层 `*Snapshot` 等). DO / DTO / Snapshot / Properties 用单行 javadoc; VO 字段用 `@Schema(description=...)` 或 javadoc 表达含义即可 (二选一, 见 §9).

单行, **写字段的业务含义** (不复述字段名), 枚举值写清楚.

- 关联字段只讲它"是什么" (`所属会员` / `所购套餐`), **禁止**写外键指向哪张表哪个列 (`FK → xxx.id`、`xxx_table.col` 这类 DB 接线信息属于 DDL / ER 图, 不进字段注释)
- 状态 / 类型字段用 `{@link XxxEnum}` 指向枚举 (不带包名, 见 §12)

```java
/** 状态: 1=正常 2=禁用 */
private Integer status;

/** 订阅状态 {@link SomeStatusEnum} */
private String subscriptionStatus;

/** 所属会员. */                        // ✅ 讲含义
private String memberUserId;

/** 会员 id; FK → member_user.id. */    // ❌ 别写外键到哪表哪列
private String memberUserId;
```

### 行内注释

只写"为什么", 不复述"做了什么".

```java
// ✅ 对: 解释非显然决策
// 用户不存在 / 密码错误统一返回 LOGIN_FAILED, 避免账户枚举
if (user == null || !bcrypt.matches(...)) throw new BusinessException(LOGIN_FAILED);

// ❌ 错: 复述代码
// 遍历用户列表
for (User u : users) { ... }
```

### 调用本类方法用 `this.` 前缀

调用当前类自己的方法 (private helper / 同类 public 方法) **一律带 `this.`**, 一眼区分"调本类"还是"调注入的依赖", 可读性更好.

```java
// ✅ 对
if (this.belowPlanSpec(cap, minTrafficGb, minBandwidthMbps)) { ... }
String key = this.regionIpKey(region, ipTypeId);

// ❌ 错: 裸方法名, 看不出是本类方法还是静态导入 / 父类方法
if (belowPlanSpec(cap, minTrafficGb, minBandwidthMbps)) { ... }
```

### 别为单处使用过度拆私有方法

私有 helper 在**多处复用 / 封装非平凡逻辑**时才抽 (如 `belowPlanSpec`、`regionIpKey` 都被 ≥2 处调用); **只一处调用且短小**的逻辑直接内联在调用处 —— 私有方法堆太多反而割裂阅读 (来回跳转), 可读性不升反降. 判据: 抽出去是让人"少看几行"还是"多跳一次".

### package-info.java

仅作占位, **只保留 `package` 声明**, 不写 Javadoc 或 `@author`.

---

## 15. 提交前检查清单

**分层与命名**
- [ ] Controller 不含业务逻辑 (下沉到 Service)
- [ ] Convert 不注入 Service (仅接收纯数据 Map / List)
- [ ] Service 接口 + Impl 分离, `@Service` 标在 Impl
- [ ] 含校验逻辑的 Service 配对 `XxxValidator`, 校验逻辑**不内联在 ServiceImpl**
- [ ] VO 后缀 ∈ `CreateReqVO / UpdateReqVO / PageReqVO / RespVO / SimpleRespVO`
- [ ] Mapper 继承 `BaseMapper<T>`, default 方法封装 Wrapper, Service 不直接构造 Wrapper
- [ ] 跨模块调用走 `com.nook.biz.<module>.api.*`, 不直注其他模块的 Service / Mapper
- [ ] 关联拼接三步走: Convert 提取 ID → Controller 批量查 → Convert 拼接

**数据处理**
- [ ] 主键 `CHAR(32)`, 实体继承 `BaseEntity`, **不重复声明** id / createdAt / updatedAt
- [ ] 关联数据批量查 `loadXxxMap(ids)`, 不是逐条查
- [ ] Wrapper 更新必须显式 `.set(SomeDO::getUpdatedAt, LocalDateTime.now())`
- [ ] 先删后增 + 唯一键场景用 `physicalDelete`, SQL 写 XML 不用 `@Delete` 注解
- [ ] DDL **无外键** (`FOREIGN KEY`); 跨表存在性走 Validator
- [ ] DDL **无 DEFAULT** 默认值; 默认值由 ServiceImpl 显式赋值 (NOT NULL 仍允许且推荐)
- [ ] 业务唯一字段双重校验: DB 层 `UNIQUE INDEX` + 代码层 `validateXxxUnique` 都不能省

**注解与校验**
- [ ] Controller 加 `@Validated`; 方法 `@RequestBody` 参数加 `@Valid`
- [ ] VO 必填加 `@NotBlank / @NotNull` 中文 message; 嵌套对象加 `@Valid`
- [ ] 注入用 `@RequiredArgsConstructor` + `private final`, **不用** `@Resource` / `@Autowired`
- [ ] VO 不用注解做枚举取值 / 业务唯一 / 跨表存在 / 跨字段条件校验, 一律走 Validator (见 §9)

**事务、日志**
- [ ] 多条 / 多表写入 `@Transactional(rollbackFor = Exception.class)`; 单条 DML 不必; 读操作不加
- [ ] 跨多表写入 + 外部调用: 外部调用拆事务外
- [ ] 日志 `@Slf4j`, 格式 `[methodName] 描述: key={}`, 异常对象放最后

**安全**
- [ ] sa-token 拦截器分流: `/admin/**` 走 system, `/customer/**` 走 member
- [ ] 错误描述不暴露内部信息 (e.g., 登录失败用统一 message, 不区分"用户不存在 / 密码错误")
- [ ] 密码用 `BCryptPasswordEncoder.encode()` 哈希, 永不返回 password_hash 字段到前端

**代码质量**
- [ ] SQL 写法分级匹配 (`default` 方法 / XML), **禁止** `@Select` / `@Update` / `@Insert` / `@Delete` 注解
- [ ] 错误码字符串格式 `<模块大写>_<三位数字>`, 实现 `ErrorCode` 接口
- [ ] 异常抛 `BusinessException(ErrorCode)`, 不直接 `throw new RuntimeException(...)`
- [ ] JSON 用 `JsonUtils`, 判空用 Hutool, 集合用 `CollectionUtils.convertXxx`
- [ ] 枚举提供 `fromCode()`; 值字段用包装类型 (`Integer`)
- [ ] 异步用独立 `XxxAsyncHelper`
- [ ] 单方法 ≤ 80 行 (不含空行/注释)
- [ ] 注释只解释"为什么", 无序号前缀, 无废弃代码, **无 bug 调查史 / 全限定名 / 跨模块叙述**
- [ ] 数据载体类 (`DO`/`VO`/`DTO`/`*Snapshot`) 每个字段都有注释, 不留裸字段
- [ ] 字段 / `@param` 注释只讲业务含义, **不写外键到哪表哪列** (`FK → xxx.id`); 状态字段用 `{@link XxxEnum}`
- [ ] `@param` / `@return` 精简 (写内容或类型即可), 不展开成整句描述
- [ ] 判空用 Hutool (`CollUtil`/`StrUtil`/`ObjectUtil` 等), 不手写 `== null` / `.isEmpty()`
- [ ] 类级 javadoc 一句话; 核心方法 `@param/@return` 标准格式; 简单方法单行
- [ ] 调用本类方法带 `this.` 前缀 (区分本类方法 vs 注入依赖)
- [ ] Validator public 方法用完整 javadoc (描述 + `@param`/`@return`), 不写单行
- [ ] 注释描述状态 / 枚举取值用 label (`生效中`) 而非 raw code (`ACTIVE` / `1`)

---

## 附录 A: 参考模块

| 想看 | 参考 |
|---|---|
| 完整 CRUD + 鉴权 + 列表分页 | `nook-module-system` `SystemUser*` |
| sa-token 登录 / 登出 | `SystemAuthServiceImpl` + `SystemAuthController` |
| Mapper default 方法封装 Wrapper | `SystemUserMapper` |
| Validator 集中校验 | `SystemUserValidator` |
| Convert MapStruct | `SystemUserConvert` |

## 附录 B: DDL / 数据迁移流程 (强调)

1. **不要**直接生成 `.sql` 迁移文件丢仓库
2. 设计完 DDL → 跟用户说明 (展示要执行的 CREATE / ALTER / INSERT) → 等用户确认
3. 用户 OK 后通过 `mcp__my-database__execute_query` 执行
4. 执行后用 `mcp__my-database__get_table_info` 校验结果
5. 仓库里只在 README / 设计文档里描述表结构, 不留迁移脚本

