# Nook 后端开发规范

> 版本: v1.6 ｜ 日期: 2026-06-11 ｜ 适用: 所有 `nook-module-*` 模块

后端开发的强制约束. 本文为 AI 阅读优化, 规则优先于示例.

> 示例代码统一用占位类名 (`SomeDO` / `SomeService` / `SomeEnum` 等), 不绑定具体业务实体.

---

## 0. Nook 项目骨架要点

| 项 | Nook 现状 | 备注 |
|---|---|---|
| 主键 | `CHAR(32)` UUID, `BaseEntity.id` 类型 `String`, `@TableId(type = IdType.ASSIGN_UUID)` | 不用雪花 Long |
| 时间字段 | `created_at` / `updated_at`, 实体字段 `createdAt` / `updatedAt`, `MetaObjectHandlerImpl` 自动 fill | 列名下划线 / 字段名驼峰 |
| 软删除 | `deleted TINYINT` (0/1), 实体 `@TableLogic Integer deleted` | |
| Mapper 基类 | MP 原生 `BaseMapper<T>`, 用 `Wrappers.lambdaQuery()` / `lambdaUpdate()` | **没有** `BaseMapperX` / `LambdaQueryWrapperX` |
| 注入 | `@Resource` 字段注入 | **不用** `@Autowired` / `@RequiredArgsConstructor` 构造注入 |
| 响应 | `com.nook.common.web.response.Result` + `Result.ok(data)` / `Result.ok()` | 不用 `CommonResult` / `success()` |
| 分页 | `com.nook.common.web.response.PageResult` + `PageResult.of(total, list)` | 入参基类 `PageParam` |
| 错误码 | `BusinessException(ErrorCode)`, `ErrorCode` 是接口, 各模块自定义枚举实现 | 不用 `new ErrorCode(数字, "msg")` |
| 鉴权 | sa-token: `StpSystemUtil` (admin) / `StpMemberUtil` (portal) 双体系隔离, loginType `system` / `member` | 不用 Spring Security `@PreAuthorize` |
| HTTP 路径前缀 | admin: `/admin/<module>/<feature>/...`; 客户端: `/portal/<module>/<feature>/...` | 跟 `SaTokenConfig` 一致 |
| Swagger | 起步**不强制** `@Tag` / `@Operation`, 欢迎补 | 后续接 OpenAPI 再统一 |
| 多租户 / 字典体系 | **不引入** | 字典用枚举 + DB 字段 |
| 跨模块 API | 模块拆 `-api` (契约: enums / DTO / Api 接口) + `-server` (实现), 参考 yudao-cloud simplified | 禁止直注其他模块 -server 的 Service / Mapper |

---

## 1. 分层职责

```
Controller  → (web 边界) 参数校验、调用 Service、调 Convert 组装 VO、返回 Result
Api 实现    → (跨模块边界) 实现本模块对外 Api; 调 Service / Mapper 拿本模块 DO → 调 Convert 组装跨模块 DTO 返回; 角色等同 Controller, 出参是 DTO
Service     → 业务逻辑、事务、缓存; 校验委托 Validator; 返回 DO, 不构建 VO / 跨模块 DTO (例外: 跨模块聚合视图, 见下)
Validator   → 集中存在性 / 唯一性 / 业务前置校验, 抛 BusinessException; 含校验逻辑的 Service 配对一个
Convert     → DO ↔ VO / DTO 映射; 只接收纯数据 Map / List; **禁止注入** Service / Api
Mapper      → 数据库访问; 继承 `BaseMapper<T>`, default 方法封装 Wrapper
```

### 跨模块调用

每个业务模块拆两 maven 子模块 (monolith 单进程, 普通 Spring DI, 不是 Feign / RPC):

- `nook-module-<name>-api` — 对外契约, 零业务依赖 (只允许 jakarta.validation / lombok 等编译期工具):
  - `api/enums/` — 枚举 (跨模块共享或本模块状态枚举, 统一放这)
  - `api/<feature>/{XxxApi, dto/XxxRespDTO...}` — 其它模块需要调用本模块时才建
- `nook-module-<name>-server` — 实现 + 私有内部 (结构见 §11)

**禁止**: 跨模块 import `nook-module-<a>-server` 包内任何类; 在 -api 模块放 Spring beans (`@Service` / `@Component`).
**允许**: A 模块 -server 依赖 B 模块 -api; 任意模块依赖 `nook-common` / `nook-framework`.

**Api 接口命名**: 对齐它主要操作的表 (`ResourceServerQuotaApi`↔`resource_server_quota`); 无单一表对应的**业务聚合 / 操作类**才用描述性后缀 (`XrayReconcileApi` 对账、`TradeBandwidthApi` 带宽聚合). **禁止**含糊后缀 ("Node" / "Info" / "Data").

### 关联数据拼接 (跨模块 / 聚合视图: Service 经 Convert 直接返 VO)

跨模块 Api 调用 / 多源聚合属**业务编排, 放 Service**: Service 查主数据 → Convert 转入参 → 调跨模块 Api → Convert 拼成 **VO** 直接返回; Controller 纯转发. **禁止** carrier record (如 `record PlanPage(page, capMap)`) 把 DO + 数据塞回 Controller.

```java
// Service: 此为 §1「Service 返 DO」的例外, 仅限"无单一 DO 对应"的跨模块 / 聚合视图 (§8 同源)
public PageResult<SomeRespVO> getSomePage(SomePageReqVO req) {
    PageResult<SomeDO> plans = somePageQuery(req);
    List<XxxSpecDTO> specs = SomeConvert.INSTANCE.toSpecs(plans.getRecords());
    Map<String, XxxDTO> capMap = xxxApi.batchLoad(specs);
    return SomeConvert.INSTANCE.convertPage(plans, capMap);
}
// Controller: return Result.ok(someService.getSomePage(req));
```

普通单表 CRUD / 单纯把本模块 DO 装配成出参, 仍 Service 返 DO + 边界层 (Controller / Api 实现) 调 Convert.

### 对外 Api 实现 = 跨模块边界 (装配 DTO 在这层, 不在 Service)

`XxxApiImpl` 角色等同 Controller: 拿本模块 DO → Convert 拼 DTO 返回. **Service 不构建跨模块 DTO**.

| 场景 | 谁装配 |
|---|---|
| 本模块 DO 暴露成对外 DTO (纯拼装: 调用方给 id, 仅 join + map, 无业务判断) | **Api 实现** |
| 需调**其他**模块 Api 聚合多源 | **Service** (见上, 直接返 VO) |
| 方法本身是核心选择 / 计算 (选址、库存), 即便出参是 DTO | 核心类 / Service, 不拆进 ApiImpl |

**DO→DTO 转换一律走 Convert**: ApiImpl 只调 `XxxApiConvert.INSTANCE.toRespDTO(do)`, **禁止**内联 `BeanUtils.toBean(do, Dto.class)` 或手写 `toDto(...)` —— 即便 1:1 全字段拷贝 (MapStruct 编译期告警字段缺漏, 优于 BeanUtils 静默漏拷). api 专用 Convert 命名 `XxxApiConvert`, 跟 ApiImpl 同放 `api/` 包 (见 §11). 注: Service 里 `reqVO → DO` 入参拷贝仍可用 `BeanUtils`.

```java
// ✅ ApiImpl 编排查询 + Convert 拼 DTO; ❌ Service 直接返跨模块 DTO
@Override
public List<XxxSummaryDTO> listSummaryByServerIds(Collection<String> ids) {
    Map<String, FooDO> fooMap = xxxService.getFooMap(ids);
    Map<String, BarDO> barMap = xxxService.getBarMap(ids);
    return XxxApiConvert.INSTANCE.toSummaries(ids, fooMap, barMap);
}
```

### 核心编排 vs 纯规则

- **有 I/O / 状态 / 事务 / 多源编排** (选址、准入、分配、计量) → 收口到核心类 (`XxxAdmission` / `XxxAllocator` / 对应 Service), 其它地方只调用不重复实现.
- **纯判定 / 纯计算** (无注入、无 I/O, 只对入参做布尔 / 数值运算) → 抽工具 / 辅助方法, 由核心类调用:
  - **入参用值字段, 禁止传 `DO` / `VO`** —— 解耦持久化类型, 可跨 DO / VO 复用、可单测; 调用方从 DO 取字段后传入.
  - 放置: 同域多条纯规则 → `XxxRules` 辅助类 (放业务模块); 通用算法 → `nook-common` 工具类; 单处 trivial 可留 `private static` (仍值入参, 见 §14「别过度拆私有方法」).

```java
// ✅ 值入参: meetsPlanSpec(Integer totalGb, Integer bandwidthMbps, int minTrafficGb, int minBandwidthMbps)
// ❌ 塞 DO:  meetsPlanSpec(ResourceServerQuotaDO quota, int minTrafficGb, int minBandwidthMbps)
```

---

## 2. 集合与判空工具

**判空一律用 Hutool, 禁止手写 `== null` / `.isEmpty()` 拼接; 禁止手写 stream 做单层映射.**

| 需求 | 写法 |
|---|---|
| 提取字段为 Set / List | `CollectionUtils.convertSet(list, SomeDO::getField)` / `convertList(...)` |
| 构建 ID → DO / 字段 Map | `CollectionUtils.convertMap(list, SomeDO::getId[, SomeDO::getField])` |
| 多集合判空 | `CollectionUtils.isAnyEmpty(c1, c2, ...)` |
| 集合 / Map / 数组判空 | `CollUtil.isEmpty` / `MapUtil.isEmpty` / `ArrayUtil.isEmpty` |
| 字符串判空 | `StrUtil.isEmpty(s)` / `StrUtil.isNotBlank(s)` |
| 对象判空 / 比较 | `ObjectUtil.isNull(obj)` / `ObjectUtil.equal(a, b)` |
| 空分页 | `PageResult.empty()`, 禁止 `new PageResult<>(emptyList(), 0L)` |

位置: `com.nook.common.utils.collection.CollectionUtils`, `com.nook.common.utils.object.BeanUtils`, Hutool `cn.hutool.core.util.*`.

---

## 3. 实体与时间字段

### 实体

- 包: `com.nook.biz.<module>.entity` (Mapper 在 `...mapper`, 见 §11). 命名: **新建实体用 `XxxDO` 后缀**; 存量裸名 (`SystemUser` / `MemberUser` / `XrayClient`) 沿用, 不强制返工. **禁止** `XxxEntity` / `XxxPO` 后缀.
- 继承 `BaseEntity` (已声明 `id` / `createdAt` / `updatedAt`), 子类**不要**再声明这三个字段.

DDL 固定三件套:
```sql
id CHAR(32) NOT NULL PRIMARY KEY COMMENT '主键ID',
created_at DATETIME NOT NULL COMMENT '创建时间',
updated_at DATETIME NOT NULL COMMENT '更新时间'
```

### 时间字段填充规则

**触发前提**: MP 仅当实体**至少一个字段带 `@TableField(fill=…)`** 时, 才在 `insert(entity)` / `updateById(entity)` 调用 `MetaObjectHandlerImpl`. 注解来自「继承 `BaseEntity`」或「DO 字段显式声明」(如 `XrayServerDO`), 两者皆可.

handler 行为:
- **insertFill** 用 `strictInsertFill` 填 `createdAt` + `updatedAt` (仅填 null 字段).
- **updateFill** 用 `setFieldValByName` **无条件刷新** `updatedAt`. **勿用** `strictUpdateFill` (只填 null, 对已查出的实体不生效 → `updated_at` 永不更新).

**两种情况 handler 不触发, 必须手动 set (属正常, 勿删)**:

1. **Wrapper 更新**: `Wrappers.lambdaUpdate().set(...)` 无实体 metaObject. Mapper default 更新方法**必须显式** `.set(SomeDO::getUpdatedAt, LocalDateTime.now())`.
2. **不继承 `BaseEntity` 且字段无 `@TableField(fill)` 的 DO** (主键非 `id` 的子表): insert / update 时间字段都不自动填, **都要手动 set**. 新建这类 DO 时建议直接在时间字段加 `@TableField(value = "created_at", fill = FieldFill.INSERT)` / `(value = "updated_at", fill = FieldFill.INSERT_UPDATE)` 免去手动.

---

## 4. 软删除与物理删除

- `BaseEntity` 子类自带 `@TableLogic Integer deleted`; MP 拦截器自动加 `WHERE deleted = 0`.
- **"先删后增 + 唯一键"场景必须物理删除**, 否则旧记录占用唯一键, INSERT 报 `Duplicate entry`.
- **物理删除一律写 XML** (注解可能绕过 `@TableLogic` 拦截器链), 方法名 `physicalDelete` 开头:

```java
int physicalDeleteByXxx(@Param("xxx") String xxx);
```
```xml
<!-- resources/mapper/<module>/XxxMapper.xml -->
<delete id="physicalDeleteByXxx">DELETE FROM xxx WHERE xxx_field = #{xxx}</delete>
```

---

## 5. Mapper 与 SQL

### 数据库结构变更 (DDL / 数据迁移)

**禁止**在仓库生成 `*.sql` 迁移脚本 (会被误当部署流程; 实际用 MCP 同步生产). 流程:
1. 跟用户说明要执行的 SQL (CREATE / ALTER / INSERT) → 等确认
2. 通过 `mcp__universal-db-mcp__execute_query` 执行 → `get_table_info` 校验结果
3. 表结构只在设计文档里描述, 不留迁移脚本

### DDL 约束 (强制)

- **禁止外键** (`FOREIGN KEY`) — 跨表引用由 Validator (`validateXxxExists`) 校验; FK 阻碍迁移 / 拆分.
- **禁止 DEFAULT 默认值** — 默认值统一由 ServiceImpl 新建路径显式赋值, 代码即文档; DDL 写 DEFAULT 是双重事实源.
- **允许且推荐 NOT NULL** — 必填语义; NOT NULL 字段漏赋值直接报错, 优于 DEFAULT 静默写出错误值.
- **唯一业务字段双重校验**: DB `UNIQUE INDEX` (兜底并发 race) + 代码 `validateXxxUnique` (用户可读错误码), **两者都不能省**.

### 写法分级

| 复杂度 | 写法 |
|---|---|
| MP 内置 | `selectById` / `insert` / `selectList` 等 |
| 动态条件单表 | Mapper `default` 方法 + `Wrappers.lambdaQuery()`, 用 `eq(条件, lambda, 值)` 三参重载做 IfPresent |
| 物理删除 | XML (见 §4) |
| 多表 JOIN / 子查询 | XML (`resources/mapper/<module>/*.xml`) |

### Mapper 写法

**Mapper 方法不写 javadoc / 注释** (§14 例外, 同 Convert 理): 方法名 + Wrapper 链已自解释; 类级 javadoc 仍保留. 「Wrapper 更新显式 set updated_at」由 §3 约束, 不在方法上复述.

```java
@Mapper
public interface SomeMapper extends BaseMapper<SomeDO> {

    default SomeDO selectByField(String field) {
        return selectOne(Wrappers.<SomeDO>lambdaQuery()
                .eq(SomeDO::getField, field));
    }

    default boolean existsByField(String field) {
        return exists(Wrappers.<SomeDO>lambdaQuery()
                .eq(SomeDO::getField, field));
    }

    default boolean existsByFieldExcludingId(String field, String excludeId) {
        return exists(Wrappers.<SomeDO>lambdaQuery()
                .eq(SomeDO::getField, field)
                .ne(SomeDO::getId, excludeId));
    }

    default IPage<SomeDO> selectPageByQuery(IPage<SomeDO> page, SomePageReqVO reqVO) {
        return selectPage(page, Wrappers.<SomeDO>lambdaQuery()
                .eq(ObjectUtil.isNotNull(reqVO.getStatus()), SomeDO::getStatus, reqVO.getStatus())
                .and(StrUtil.isNotBlank(reqVO.getKeyword()), q -> q
                        .like(SomeDO::getName, reqVO.getKeyword())
                        .or().like(SomeDO::getEmail, reqVO.getKeyword()))
                .orderByDesc(SomeDO::getCreatedAt));
    }

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
- Service 中拼接 SQL 字符串
- Service 中直接构造 `Wrappers` (封装到 Mapper default, Service 不感知 Wrapper)

---

## 6. 异常与错误码

每模块一个 `XxxErrorCode` 枚举, 实现 `com.nook.common.web.error.ErrorCode` 接口. 消息模板用 `%s` 占位 (非 `{}`).

```java
@Getter
@RequiredArgsConstructor
public enum SomeErrorCode implements ErrorCode {

    SOME_NOT_FOUND(9001, "XX不存在"),
    NAME_EXISTS(9003, "名称 %s 已存在"),
    ;

    private final int code;
    private final String message;
}
```

错误码段位 (int 千位; 新增错误码**先 grep 现有 `*ErrorCode` 避免撞段**):

| 段位 | 归属 |
|---|---|
| 1xxx | 通用 `CommonErrorCode` (nook-common; UNAUTHORIZED / FORBIDDEN / PARAM_INVALID / INTERNAL_ERROR 等直接引用) |
| 2xxx | system (`SystemErrorCode`) |
| 3xxx | member (`MemberErrorCode`) |
| 4xxx | trade (`TradeErrorCode`) |
| 5xxx | node 资源 (`ResourceErrorCode`) |
| 6xxx | node Xray (`XrayErrorCode`) |
| 7xxx | framework SSH (`SshErrorCode` 70xx / `ScriptErrorCode` 71xx) |
| 8xxx | operation (`OpErrorCode`) |
| 9xxx | agent (`AgentErrorCode`) |

抛异常: `throw new BusinessException(SomeErrorCode.NAME_EXISTS, name);` 全局异常处理器 (nook-framework) 自动转 `Result.fail(code, msg)`. **禁止**直接 `throw new RuntimeException(...)`.

---

## 7. Controller

### 路径风格 (强制)

1. **禁止** `@PathVariable` (`/{id}` 等). 标识一律 `@RequestParam` query string.
2. **所有 endpoint** 必须 `<动词>-<名词>[-<修饰>]`; 禁止纯名词 (`/capacity`) 或纯动词 (`/create`).

| ✅ | ❌ |
|---|---|
| `GET /get-server?id=...` | `GET /get` 或 `GET /{id}` |
| `PUT /update-quota?id=...` | `PUT /quota` 或 `PUT /{id}/quota` |
| `POST /create-frontline` | `POST /create` |
| `GET /page-landing` / `GET /list-region` | `GET /page` / `GET /list` |

**例外 (保留惯例)**: 鉴权端点 `/login` `/logout` `/register` `/me`; Agent push 协议 `/api/agent/*` (`/heartbeat` `/tasks` 等); 订阅公开 URL `/portal/sub/{token}` (外部客户端导入, 路径式 token 行业惯例, 允许 `@PathVariable`).

### 类与方法

```java
@RestController
@RequestMapping("/admin/<module>/<feature>")     // 客户端为 /portal/<module>/<feature>
@Validated
public class SomeController {

    @Resource
    private SomeService someService;

    @PostMapping("/create-some")
    public Result<String> create(@RequestBody @Valid SomeCreateReqVO reqVO) {
        SomeDO created = someService.create(reqVO);
        return Result.ok(created.getId());
    }

    @PutMapping("/update-some")
    public Result<Void> update(@RequestParam("id") String id, @RequestBody @Valid SomeUpdateReqVO reqVO) {
        someService.update(id, reqVO);
        return Result.ok();
    }

    @GetMapping("/page-some")
    public Result<PageResult<SomeRespVO>> page(@Valid SomePageReqVO reqVO) {
        // 分页查询
        PageResult<SomeDO> page = someService.page(reqVO);
        // 转换返回
        List<SomeRespVO> list = SomeConvert.INSTANCE.convertList(page.getList());
        return Result.ok(PageResult.of(page.getTotal(), list));
    }

    @GetMapping("/get-some")
    public Result<SomeRespVO> get(@RequestParam("id") String id) {
        // 查询
        SomeDO entity = someService.findById(id);
        // 转换返回
        return Result.ok(SomeConvert.INSTANCE.convert(entity));
    }

    @DeleteMapping("/delete-some")
    public Result<Void> delete(@RequestParam("id") String id) {
        someService.delete(id, StpSystemUtil.getLoginIdAsString());
        return Result.ok();
    }
}
```

### 鉴权

sa-token 拦截器在 `SaTokenConfig` 统一分流, **禁止** Controller 方法内 `StpXxxUtil.checkLogin()` 零散校验.

| 路径前缀 | StpLogic |
|---|---|
| `/admin/**` | system (`StpSystemUtil`) |
| `/portal/**` | member (`StpMemberUtil`) |
| `/admin/system/auth/login`, `/portal/<module>/auth/{login,register,logout}`, `/portal/sub/**` | 放行 |

### 依赖注入与返回

- `@Resource` 字段注入. **禁止** `@Autowired` / `@RequiredArgsConstructor` 构造注入 (指 Spring bean 依赖注入; 枚举 / ErrorCode 值构造的 lombok `@RequiredArgsConstructor` / `@AllArgsConstructor` 不在此限).
- **字段名 = 类型名的全驼峰, 禁止简称**: `xrayClientProvisionApi` (非 `provisionApi`). 存量简称重构时一并改全名.
- 字段声明用 import 短名, **禁止**全限定名 (见 §12).
- 返回 `Result.ok(...)`, **禁止**手动 `new RespVO(...)` 逐字段赋值 (走 Convert).
- **方法 javadoc 用标准格式** (说明 + `@param` / `@return`, 见 §14); Controller 方法不属于"简单透传可单行"白名单.
- 方法体遵守「业务调用分步分行」(§14): Service 查询与 Convert 转换各占一行落变量, 不嵌套内联.

---

## 8. Convert

```java
@Mapper
public interface SomeConvert {

    SomeConvert INSTANCE = Mappers.getMapper(SomeConvert.class);

    SomeRespVO convert(SomeDO entity);

    List<SomeRespVO> convertList(List<SomeDO> list);

    SomeDO convert(SomeCreateReqVO vo);

    default List<SomeRespVO> convertListWithMemberName(List<SomeDO> list, Map<String, String> memberNameMap) {
        List<SomeRespVO> voList = this.convertList(list);
        for (SomeRespVO vo : voList) {
            vo.setMemberName(memberNameMap.get(vo.getMemberUserId()));
        }
        return voList;
    }
}
```

`default` 方法只接收纯数据 Map / List, **禁止**注入或传入 Service.

### Convert 调用方边界 (强制)

**核心判定: Convert 的输入来自哪一层, 就由那一层的调用者触发翻译.**

| Convert 输入 | 调用方 |
|---|---|
| 业务实体 DO → VO (web 出参) | **Controller** |
| 业务实体 DO → 跨模块 DTO (纯拼装出参) | **Api 实现** (`XxxApiConvert`, 见 §1) |
| 基础设施 Snapshot (framework 层数据载体) | **Service** (Controller 不该 import framework 内部结构) |
| 多源聚合视图 (调其他模块 Api, 无单一 DO 对应) | **Service** (直接返 VO, 见 §1) |

```java
// ❌ Controller 接 Service 漏出的 framework snapshot 再调 Convert → framework 暴露到 web 层; 修法: Service 内拼好 VO 返出
// ❌ Service 接口返 DO, Impl 却调 Convert 拼了 VO 又丢弃 → 职责混乱

// ✅ Service 调 Convert (snapshot → VO, 无对应持久化 DO):
@Override
public SomeStatusRespVO getStatus(String serverId) {
    FooSnapshot foo = someProbe.readFoo(serverId);
    BarSnapshot bar = someProbe.readBar(serverId);
    return SomeOpsConvert.INSTANCE.toStatusRespVO(foo, bar);
}
```

### Convert 注释规则 (§14 例外)

Convert 是纯字段映射, 方法名 + `@Mapping` 已自解释:

- **不写类级 / 方法级 javadoc**, 不写 `@Mapping` 解释性行内注释
- 仅**非平凡映射** (跨类型转换 / `expression` / `qualifiedBy`) 可在该 `@Mapping` 上一行加单行注释解释为什么

---

## 9. VO 命名

| 后缀 | 用途 | 对应 endpoint |
|---|---|---|
| `CreateReqVO` | 创建 | POST `/create-xxx` |
| `UpdateReqVO` | 更新 | PUT `/update-xxx?id=` |
| `PageReqVO` | 分页查询, **继承 `PageParam`** (自带 `pageNo` / `pageSize`) | GET `/page-xxx` |
| `RespVO` | 标准响应 | 出参 |
| `SimpleRespVO` | 下拉等精简响应 | 出参 |

Create / Update 用独立 VO, **不**合并成 `SaveReqVO`. **禁止** `DTO` / `Result` / `Form` 等其他后缀.

### 字段校验

```java
@Data
public class SomeCreateReqVO {

    /** 名称. */
    @NotBlank(message = "名称不能为空")
    @Size(max = 100, message = "名称长度不能超过 100")
    private String name;

    /** 邮箱. */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    /** 明细列表. */
    @Valid                              // 嵌套必须加, 否则不触发内部校验
    private List<NestedReqVO> items;
}
```

字段含义用单行 javadoc 或 `@Schema(description=...)` 二选一 (见 §14 字段级); `@Schema` 非强制.

**允许的注解** (结构性 / 边界值): `@NotBlank` / `@NotNull` / `@Size` / `@Min` / `@Max` / `@Email` / `@Pattern`(仅简单字符集格式) / `@Valid`(嵌套必加).

**禁止用注解, 必须走 Validator** (注解列举枚举值会漏改; 业务校验靠近 mapper / 错误码上下文):

| 校验场景 | 正确做法 |
|---|---|
| 枚举取值范围 (~~`@Pattern(regexp="A\|B\|C")`~~ / ~~`@Min(1)@Max(2)`~~) | Validator 内 `Enum.fromCode() != null` |
| 业务唯一性 | `validateXxxUnique` 查 `existsByXxx[ExcludingId]` |
| 存在性 (跨表引用, 无 FK) | `validateXxxExists` |
| 跨字段条件 (A 非空时 B 必填) | Validator 内 if-then 抛 `BusinessException` |
| 复杂格式 / 业务规则 | Validator 内显式判断 |

---

## 10. Service

### 接口 + Impl 分离, 校验委托 Validator

```java
public interface SomeService {
    SomeDO create(SomeCreateReqVO reqVO);
    void update(String id, SomeUpdateReqVO reqVO);
    SomeDO findById(String id);
    PageResult<SomeDO> page(SomePageReqVO reqVO);
}

@Service
public class SomeServiceImpl implements SomeService {

    @Resource
    private SomeMapper someMapper;
    @Resource
    private SomeValidator someValidator;

    @Override
    public SomeDO create(SomeCreateReqVO reqVO) {
        // 校验唯一性等业务前置
        someValidator.validateForCreateOrUpdate(null, reqVO.getName(), reqVO.getEmail());
        // 入参拷贝并落库
        SomeDO entity = BeanUtils.toBean(reqVO, SomeDO.class);
        someMapper.insert(entity);
        return entity;
    }
}
```

### 事务

- **多条 / 多表写入**必须 `@Transactional(rollbackFor = Exception.class)` (默认只回滚 RuntimeException).
- **单条 DML 不必加** (本身原子); 前面有校验读、后面跟外部调用也不需要. **读操作不加**.
- 跨多表写入 + 外部调用 (HTTP / SSH): 事务内只做 DB, 外部调用拆事务外.

### 异步

`@Async` 必须放独立 `XxxAsyncHelper` 组件 (Service 自调用 AOP 代理失效):

```java
@Component
public class SomeAsyncHelper {

    @Resource
    private SomeServiceImpl someService;

    @Async("someExecutor")
    public void doAsync(...) {
        try { someService.doSomething(...); }
        catch (Exception e) { log.error("[doAsync] 执行异常", e); }
    }
}
```

### Validator (强制: 含校验逻辑的 Service 配对独立 `XxxValidator`)

校验逻辑 (存在性 / 唯一性 / 业务前置, 分工见 §9) 集中到 `XxxValidator`, **不内联在 ServiceImpl**; 命名与 Service 一一对应; 简单读 / 透传 Service 可省.

```java
@Component
public class SomeValidator {

    @Resource
    private SomeMapper someMapper;

    /**
     * 聚合校验入口 (Create / Update 共用)
     *
     * @param id    当前行 id, null 表示 Create
     * @param name  名称
     * @param email 邮箱
     * @return Update 路径返回当前实体, Create 路径返回 null
     */
    public SomeDO validateForCreateOrUpdate(String id, String name, String email) {
        SomeDO existing = this.validateExists(id);
        this.validateNameUnique(id, name);
        this.validateEmailUnique(id, email);
        return existing;
    }

    /**
     * 校验存在; id 为 null 直接返回 null (Create 路径)
     *
     * @param id 主键ID
     * @return 实体
     */
    public SomeDO validateExists(String id) {
        if (id == null) return null;
        SomeDO e = someMapper.selectById(id);
        if (ObjectUtil.isNull(e)) throw new BusinessException(SomeErrorCode.NOT_FOUND);
        return e;
    }

    /**
     * 校验名称唯一; id 非空时排除自身 (Update 不冲突自己)
     *
     * @param id   当前行 id, Create 传 null
     * @param name 名称
     */
    public void validateNameUnique(String id, String name) {
        if (StrUtil.isBlank(name)) return;
        boolean dup = id == null
                ? someMapper.existsByName(name)
                : someMapper.existsByNameExcludingId(name, id);
        if (dup) throw new BusinessException(SomeErrorCode.NAME_EXISTS, name);
    }
}
```

**约定**:
- 位置 `validator/XxxValidator.java`; `@Component` + `@Resource` 注入
- 方法命名: `validateXxxExists` / `validateXxxUnique` / `validateXxxForCreateOrUpdate` / `validateXxxValue`
- 同模块多 Service 可复用同一 Validator; **跨模块**走 `XxxApi`, 不让其他模块直注 Validator
- `@NotBlank` 等结构校验仍由 `@Valid` 在 Controller 层完成 (分工见 §9)
- `validateXxxUnique` 必须配 DB `UNIQUE INDEX` (见 §5 双重校验)

---

## 11. 模块结构 (maven 双子模块)

每个业务模块 = 父 pom (`packaging=pom`) + `-api` + `-server`:

```
nook-module-<name>/
├── pom.xml
├── nook-module-<name>-api/             # 契约: 零业务依赖
│   └── src/main/java/com/nook/biz/<name>/
│       └── api/
│           ├── enums/                  # 枚举 (本模块状态 / 跨模块共享, 统一放这)
│           │   └── XxxStatusEnum.java
│           └── <feature>/              # 需被其它模块调用时才建
│               ├── XxxApi.java         # 普通 Java interface (非 Feign)
│               └── dto/XxxRespDTO.java
└── nook-module-<name>-server/          # 实现; 依赖 -api + starter + 跨模块 -api
    └── src/main/java/com/nook/biz/<name>/
        ├── api/                        # XxxApi 实现, 跟接口同包 (跨 jar split-package)
        │   ├── XxxApiImpl.java         #   @Service, implements XxxApi
        │   └── XxxApiConvert.java      #   对外 DTO 转换, 只服务跨模块出参, 不放 convert/
        ├── controller/<feature>/       # 单端模块按 feature 分包; 双端按端分包 (见下)
        │   ├── XxxController.java
        │   └── vo/Xxx{Create,Update,Page}ReqVO.java, XxxRespVO.java
        ├── service/
        │   ├── XxxService.java
        │   └── impl/XxxServiceImpl.java
        ├── entity/XxxDO.java           # MP 实体 (存量裸名沿用, 见 §3)
        ├── mapper/XxxMapper.java
        ├── convert/<feature>/XxxConvert.java
        ├── constant/XxxErrorCode.java
        ├── framework/<feature>/        # 模块私有 config / interceptor / arg-resolver
        └── validator/XxxValidator.java
```

### 双端分包 + Portal 前缀 (admin / portal)

模块同时服务管理端与客户端时:

- **端专属层** (Controller / Service / Convert / VO) 按端拆子包: `controller/{admin,portal}/`、`service/{admin,portal}/impl/`、`convert/{admin,portal}/`.
- **客户端类名一律 `Portal` 前缀; 管理端默认无前缀.** 理由: Spring bean 名 = 简单类名 (不含包名), 两端同简单名直接 `ConflictingBeanDefinitionException` 起不来; 前缀后 bean 名与注入字段名天然唯一.
- **共享层不分端、单份、无前缀**: Mapper / 实体 / `XxxApiImpl` + `XxxApiConvert` / Validator / ErrorCode.
- 单端模块不必分端子包, 按 feature 组织.

`nook-module-member` 已落地参考:

```
controller/admin/    MemberController + vo/...
controller/portal/   PortalAuthController, PortalMemberController + vo/Portal*.java
service/admin/       MemberService + impl/MemberServiceImpl
service/portal/      PortalMemberAuthService, PortalMemberService + impl/Portal*ServiceImpl
convert/admin/       MemberUserConvert
convert/portal/      PortalMemberUserConvert
共享层 (不分端):     mapper/MemberUserMapper · entity/MemberUser · api/MemberUserApiImpl + MemberUserApiConvert · validator/MemberUserValidator · constant/MemberErrorCode
```

---

## 12. 枚举

### 强制: 状态 / 类型 / 分类字段必须有枚举类

字段语义是"有限取值集合" (生命周期 / 状态 / 角色 / 类型 / 协议 等) 时, DB 列存 code, Java 必须配套 Enum:

- 位置: `nook-module-<name>-api` 的 `api/enums/` (全模块统一, 即使暂仅模块内部用); 命名 `XxxEnum`.
- **严禁**代码里散落 `"AVAILABLE"` / `1` / `2` 魔法字面量, 一律 `Enum.fromCode(...)` / `Enum.matches(...)` / `Enum.getCode()`.
- DO 字段保持原始类型 (`Integer` / `String`), 不直接持有 Enum (不引 typeHandler); javadoc 用 `{@link XxxEnum}` 指向枚举.

### 强制: 枚举骨架

```java
@Getter
@AllArgsConstructor
public enum SomeStatusEnum {

    NORMAL(1, "正常"),
    DISABLED(2, "禁用"),
    ;

    private final Integer code;
    private final String label;

    public static SomeStatusEnum fromCode(Integer code) {
        if (code == null) return null;
        for (SomeStatusEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }

    public boolean matches(Integer code) { return this.code.equals(code); }
}
```

字符串型同构: 字段换 `String state` + `String label`, 方法为 `fromState` / `matches(String)`; state 大小写跟 DB 约束一致. 比较一律 `Enum.matches(field)`, **禁止** `"AVAILABLE".equals(do.getStatus())`.

### 强制: 不用全限定名

类型引用一律 `import`, **禁止** `java.util.Map<String, com.nook.biz.some.api.dto.SomeRespDTO>` 这种全限定写法; 包内冲突用重命名变量解决. 枚举 javadoc 引用同理: `{@link SomeLifecycleEnum}` 不带包名, IDE 解析靠 import.

---

## 13. 日志

```java
log.info("[login] 登录成功: userId={}, ip={}", userId, ip);
log.error("[provisionUser] 调用 CF 失败: subId={}", subId, e);    // 异常对象放最后
```

格式: `[methodName] 描述: key={}, key={}`; 类上 `@Slf4j`; **禁止**字符串拼接.

| 级别 | 场景 |
|---|---|
| ERROR | 影响业务 / 需人工介入 (外部调用失败, 数据不一致) |
| WARN | 可恢复 / 重试 / 降级 / 用户操作错误 (如登录失败) |
| INFO | 关键业务节点 (注册, 登录, 订阅切换) |
| DEBUG | 调试用 (生产关闭) |

---

## 14. 注释规范

**原则: 代码自解释, 注释只解释"为什么".**

### 必须加

1. 多步骤业务流程 — 每步业务含义小标题 (**不带** `1./2./3.` 编号前缀)
2. 非显然决策 — 设计取舍 / 踩坑规避 / 安全考虑
3. 反直觉行为 — 说明原因

### 推荐 (默认加)

- **导读式步骤注释**: 方法体 **≥2 个不同步骤** (校验 / 取数 / 转换 / 调用) 时, **默认每步加单行小标题** (`// 校验会员存在` / `// 转换返回`); 简单 CRUD 同样适用; Controller / Service / ApiImpl 各层都加.
- 单步透传方法不加; 同一方法内"要么每步都标、要么都不标", 不半标.

### 禁止加

- 复述**单行代码**字面意思 (`// 遍历字段列表`) — 区别于导读式步骤注释 (标业务步骤, 允许)
- 自解释变量的用途说明 (`// 用户 ID`); 序号前缀 `// 1.`
- 注释掉的废弃代码 (直接删, git 有历史); bug 调查史 / 版本踩坑 (属 commit message)
- **跨模块叙述** ("不下沉到 xxx 模块") — 属设计文档
- **内部机制黑话 / 英文术语** — 用中文业务说法: `provision`→开通, `allocator`→选址, `enrich`→填充, `dispatch`→派发; 领域标准词 (SSH / vmess / inbound / 对账) 可保留

### 类级 Javadoc (yudao 多行风格, 全系统统一)

**所有类** (Controller / Service / Validator / Mapper / DO / VO / framework 基础设施 / 注解 / Helper 等) 用多行格式 + `@author nook`; 主体**一行职责短语** (无句号, 不展开实现细节 / how / 踩坑史). **禁止**单行类级注释. **例外: Convert 不写任何 javadoc** (见 §8).

```java
/**
 * 管理后台 - XX Controller
 *
 * @author nook
 */
@RestController
public class SomeController { ... }

/**
 * XX Service 实现类
 *
 * @author nook
 */
@Service
public class SomeServiceImpl implements SomeService { ... }
```

| 类型 | 主体格式 |
|---|---|
| Service 接口 / ServiceImpl | `<业务名称> Service 接口` / `<业务名称> Service 实现类` |
| `XxxApiImpl` | `<业务名称> Api 实现类` |
| Validator | `<业务名称> 业务校验` |
| Controller | `管理后台 - <业务名称> Controller` / `客户端 - <业务名称> Controller` / 内部端点不带端前缀 |
| DO / Mapper | `<业务名称> DO` / `<业务名称> Mapper` |
| Request / Response VO | `管理后台/客户端 - <业务名称><动作> Request VO` / `... Response VO` |
| framework 基础设施 | `<职责短语>` (跟类名对应) |
| 单元测试 | `<业务名称> 单元测试` |
| Convert | — (不写) |

`@author nook` 统一作者名 (不用人名 / 邮箱); 注解 `@interface` 同格式.

### 方法级 Javadoc

**默认标准格式** (说明 + `@param` + `@return`, void 可省 `@return`).
单行白名单**仅**: `private` helper / `@Override` 平凡方法. Controller 方法**不算**"简单透传", 必须标准格式.
**例外 (完全不写)**: Convert (§8) 与 Mapper (§5).

- `@param` 写业务含义 (`会员ID`), **禁止**写对应表列 (`member_user.id 集合`)
- `@return` 写内容或类型即可 (`分页列表` / `Map<String, String>`), 不展开整句; 返 null / 空的边界说明放首行描述
- **描述状态用枚举 label (人话)**: `生效中` 而非 `ACTIVE` / `1` — 适用于 javadoc / 行内 / 字段注释
- **只写"做什么", 不写"在哪用 / 调用场景"** (`删套餐前置` 这类) — 调用关系交给 IDE 查找用法

#### Service 接口方法 (yudao 极简风格, 强制)

首行动作短语 (动词 + 宾语, 无句号、无实现细节; "拼 yaml 写远端" / "CAS 防并发" 这类 how 放方法体行内注释):

```java
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
```

ServiceImpl 的 `@Override` 方法**不重复写** (IDE 继承接口 javadoc). **Validator public 方法用完整标准 javadoc** (被多处复用, 单行说不清), 见 §10 示例.

### 字段级 Javadoc

所有数据载体类 (`DO` / `VO` / `DTO` / `*Snapshot` / Properties) **每个字段都要有注释, 不留裸字段**; 单行写业务含义 (不复述字段名); VO 可用 `@Schema(description=...)` 替代 (二选一).

```java
/** 订阅状态 {@link SomeStatusEnum} */     // ✅ 状态字段指向枚举
private String subscriptionStatus;

/** 所属会员. */                            // ✅ 只讲含义
/** 会员 id; FK → member_user.id. */        // ❌ 禁止写外键到哪表哪列 (属 DDL / ER 图)
private String memberUserId;
```

### 行内注释

只写"为什么", 不复述"做了什么":

```java
// ✅ 用户不存在 / 密码错误统一返回 LOGIN_FAILED, 避免账户枚举
// ❌ // 遍历用户列表
```

### 业务调用分步分行 (全层强制)

方法体内每个业务步骤 (Service / Mapper / Convert 调用、加密、生成 token、解析 IP 等) **独立成行, 结果落局部变量**; **禁止**把一个业务调用内联塞进另一个调用的实参. Controller / Service / ApiImpl / Validator 全层适用.

```java
// ✅ 查询、转换分行, 每步结果有名字
SomeDO entity = someService.findById(id);
return Result.ok(SomeConvert.INSTANCE.convert(entity));

// ❌ 业务调用嵌进另一个调用的实参
return Result.ok(SomeConvert.INSTANCE.convert(someService.findById(id)));
someMapper.updatePasswordHash(id, encoder.encode(password));   // 加密结果应先落变量再传
```

**不算**业务调用、可内联: 纯参数构造 (`Page.of(no, size)`)、常量 / 枚举取值 (`SomeEnum.NORMAL.getCode()`)、包装最终变量 (`Result.ok(vo)` / `PageResult.of(total, list)`).

### 调用本类方法用 `this.` 前缀

调本类方法 (private helper / 同类 public) **一律带 `this.`**, 一眼区分"调本类"还是"调注入依赖": `this.filterAllocatable(serverIds)`.

### 别为单处使用过度拆私有方法

私有 helper 在**多处复用 / 封装非平凡逻辑**时才抽; 只一处调用且短小的逻辑直接内联 — 判据: 抽出去是让人"少看几行"还是"多跳一次".

### package-info.java

仅占位, **只保留 `package` 声明**, 不写 Javadoc / `@author`.

---

## 15. 提交前检查清单

**分层与命名**
- [ ] Controller 不含业务逻辑; Convert 不注入 Service
- [ ] Service 接口 + Impl 分离, `@Service` 标在 Impl; 含校验逻辑的 Service 配对 `XxxValidator`, 校验不内联在 Impl
- [ ] VO 后缀 ∈ `CreateReqVO / UpdateReqVO / PageReqVO / RespVO / SimpleRespVO`; PageReqVO 继承 `PageParam`
- [ ] Mapper 继承 `BaseMapper<T>`, default 方法封装 Wrapper, Service 不直接构造 Wrapper
- [ ] 跨模块调用走 `-api` 的 `XxxApi`, 不直注其他模块 Service / Mapper; ApiImpl 用 `XxxApiConvert` 拼 DTO
- [ ] 跨模块 / 聚合视图: Api 调用放 Service 并经 Convert 直接返 VO (禁 carrier record); 普通 CRUD 仍边界层调 Convert
- [ ] endpoint `<动词>-<名词>`, 无 `@PathVariable` (例外见 §7); 注入字段名 = 类型名全驼峰

**数据处理**
- [ ] 实体继承 `BaseEntity`, 不重复声明 id / createdAt / updatedAt; 新建实体 `DO` 后缀
- [ ] 关联数据批量查 (`getXxxMap(ids)`), 不逐条查
- [ ] Wrapper 更新显式 `.set(SomeDO::getUpdatedAt, LocalDateTime.now())`
- [ ] 先删后增 + 唯一键用 `physicalDelete` (XML, 非 `@Delete`)
- [ ] DDL 无外键、无 DEFAULT; NOT NULL 字段 ServiceImpl 显式赋值
- [ ] 业务唯一字段: DB `UNIQUE INDEX` + `validateXxxUnique` 双重校验

**注解与校验**
- [ ] Controller 加 `@Validated`; `@RequestBody` 参数加 `@Valid`
- [ ] VO 必填加 `@NotBlank / @NotNull` 中文 message; 嵌套加 `@Valid`
- [ ] `@Resource` 字段注入, 不用 `@Autowired` / `@RequiredArgsConstructor` 构造注入
- [ ] 枚举取值 / 业务唯一 / 存在性 / 跨字段校验走 Validator, 不用 VO 注解 (见 §9)

**事务、日志、安全**
- [ ] 多条 / 多表写入 `@Transactional(rollbackFor = Exception.class)`; 单条 DML 不加; 读不加; 外部调用拆事务外
- [ ] 日志 `@Slf4j`, 格式 `[methodName] 描述: key={}`, 异常对象放最后
- [ ] 鉴权走 sa-token 拦截器分流 (`/admin/**` system, `/portal/**` member), 不在方法内零散 checkLogin
- [ ] 登录失败统一 message 防账户枚举; 密码 BCrypt 哈希, 永不返回 password_hash

**代码质量**
- [ ] 无 SQL 注解 (`@Select` 等); 错误码 int 千位段位不撞段, 实现 `ErrorCode` 接口; 异常抛 `BusinessException`
- [ ] JSON 用 `JsonUtils`; 判空用 Hutool, 集合用 `CollectionUtils.convertXxx`, 不手写 stream 单层映射
- [ ] 状态字段配枚举 (`api/enums/`), 无魔法字面量; 枚举提供 `fromCode` / `matches`
- [ ] 异步用独立 `XxxAsyncHelper`; 单方法 ≤ 80 行 (不含空行 / 注释)
- [ ] 业务调用分步分行, 不内联嵌进另一调用实参; 方法体 ≥2 步时每步加导读小标题 (见 §14)
- [ ] 类级 javadoc 多行 + `@author nook`; Controller / Service 接口 / Validator 方法标准 javadoc; Convert / Mapper 方法不写
- [ ] 数据载体类无裸字段; 字段 / `@param` 只讲业务含义 (不写 FK 表列); 状态用 `{@link XxxEnum}` + label 人话
- [ ] 注释只解释"为什么": 无序号前缀 / 废弃代码 / bug 调查史 / 全限定名 / 跨模块叙述 / 调用场景
- [ ] 调本类方法带 `this.` 前缀

---

## 附录 A: 参考实现

| 想看 | 参考 |
|---|---|
| 完整 CRUD + 鉴权 + 分页 | `nook-module-system` `SystemUser*` |
| sa-token 登录 / 登出 | `SystemAuthServiceImpl` + `SystemAuthController` |
| Mapper default 封装 Wrapper | `SystemUserMapper` / `MemberUserMapper` |
| Validator 集中校验 | `SystemUserValidator` / `MemberUserValidator` |
| 双端分包 + Portal 前缀 | `nook-module-member` (见 §11) |
| `-api` / `-server` 拆分 + ApiImpl | `nook-module-agent`, `nook-module-member` |
