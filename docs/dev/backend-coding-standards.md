# Nook 后端开发规范

> 版本: v1.0 ｜ 日期: 2026-05-20 ｜ 适用: 所有 `nook-module-*` 模块

后端开发的强制约束. 本文为 AI 阅读优化, 规则优先于示例. 参考 datahub `backend-coding-standards.md v1.1`, 结合 Nook 现状裁剪.

---

## 0. Nook 项目骨架要点 (跟 datahub 的差异)

| 项 | Nook 现状 | 备注 |
|---|---|---|
| 主键 | `CHAR(32)` UUID, `BaseEntity.id` 类型 `String`, `@TableId(type = IdType.ASSIGN_UUID)` | 不用雪花 Long |
| 时间字段 | `created_at` / `updated_at`, 实体字段 `createdAt` / `updatedAt`, `MetaObjectHandlerImpl` 自动 fill | 跟 datahub `createTime` 不同 |
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
Service     → 业务逻辑、事务、缓存; 返回 Entity, 不构建 VO
Validator   → 集中存在性 / 唯一性 / 业务前置校验, 抛 BusinessException (跟 datahub 的 Helper 类似)
Convert     → Entity ↔ VO; 接收纯数据 Map / List 拼接, 禁止注入 Service
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
  - `validator/XxxValidator.java` (可选)

**禁止**:
- 跨模块 import `nook-module-<a>-server` 包内任何类
- 在 -api 模块放 Spring beans (`@Service` / `@Component` / `@Repository`) 或注解处理逻辑

**允许**:
- 模块 A 的 -server 依赖模块 B 的 -api (拿 Api 接口 + DTO 走 Spring DI)
- 任意模块依赖 `nook-common` / `nook-framework`

参考: `nook-module-agent` 的 `-api` / `-server` 拆分.

### 关联数据拼接 (三步走)

```java
// Controller
List<SomeEntity> list = someService.list();
Set<String> userIds = SomeConvert.INSTANCE.extractMemberIds(list);           // ① Convert 提取 ID
Map<String, String> userNameMap = memberUserApi.loadUserNameMap(userIds);    // ② Controller 批量查
return Result.ok(SomeConvert.INSTANCE.convertListWithInfo(list, userNameMap)); // ③ Convert 拼接
```

Convert 的 `convertListWithInfo` 只接收纯数据 Map, **禁止**接收 Service.

---

## 2. 集合与判空工具

| 需求 | 写法 |
|---|---|
| 提取字段为 Set (去重) | `CollectionUtils.convertSet(list, Entity::getField)` |
| 提取字段为 List (过滤 null) | `CollectionUtils.convertList(list, Entity::getField)` |
| 构建 ID → Entity 的 Map | `CollectionUtils.convertMap(list, Entity::getId)` |
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

### 主键: CHAR(32) UUID

```java
public class SomeEntity extends BaseEntity {
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

`Wrappers.lambdaUpdate().set(...).eq(...)` 这种写法**不会**触发 `MetaObjectHandlerImpl` 的 INSERT_UPDATE fill. 在 Mapper 的 default 更新方法里**必须显式** `.set(Entity::getUpdatedAt, LocalDateTime.now())`. 参考 `SystemUserMapper.updateLastLogin`.

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
public interface SomeMapper extends BaseMapper<SomeEntity> {

    /** 按某字段精确查找; 找不到返回 null. */
    default SomeEntity selectByField(String field) {
        return selectOne(Wrappers.<SomeEntity>lambdaQuery()
                .eq(SomeEntity::getField, field));
    }

    /** 某字段是否存在. */
    default boolean existsByField(String field) {
        return exists(Wrappers.<SomeEntity>lambdaQuery()
                .eq(SomeEntity::getField, field));
    }

    /** 排除指定 id 的存在性检查 (更新时查重). */
    default boolean existsByFieldExcludingId(String field, String excludeId) {
        return exists(Wrappers.<SomeEntity>lambdaQuery()
                .eq(SomeEntity::getField, field)
                .ne(SomeEntity::getId, excludeId));
    }

    /** 列表分页. */
    default IPage<SomeEntity> selectPageByQuery(IPage<SomeEntity> page, SomePageReqVO reqVO) {
        return selectPage(page, Wrappers.<SomeEntity>lambdaQuery()
                .eq(ObjectUtil.isNotNull(reqVO.getStatus()), SomeEntity::getStatus, reqVO.getStatus())
                .and(StrUtil.isNotBlank(reqVO.getKeyword()), q -> q
                        .like(SomeEntity::getName, reqVO.getKeyword())
                        .or().like(SomeEntity::getEmail, reqVO.getKeyword()))
                .orderByDesc(SomeEntity::getCreatedAt));
    }

    /** 部分字段更新; 用 Wrapper 更新时必须显式 set updated_at. */
    default int updateXxx(String id, String newValue) {
        return update(null, Wrappers.<SomeEntity>lambdaUpdate()
                .set(SomeEntity::getXxx, newValue)
                .set(SomeEntity::getUpdatedAt, LocalDateTime.now())
                .eq(SomeEntity::getId, id));
    }
}
```

### 禁止

- SQL 注解 (`@Select` / `@Update` / `@Insert` / `@Delete`), 全部走 `default` 方法或 XML
- Service 中拼接 SQL 字符串 (注入风险)
- Service 中直接构造 `Wrappers`, 应封装到 Mapper `default` 方法 (Service 不感知 Wrapper)

---

## 6. 异常与错误码

### 错误码定义 (跟 datahub 不同, Nook 用 `ErrorCode` 接口)

每模块一个 `XxxErrorCode` 类 (枚举或常量类), 实现 `com.nook.common.web.error.ErrorCode` 接口.

```java
// nook-common 已定义的全局错误码 (引用即可)
CommonErrorCode.UNAUTHORIZED         // 未登录
CommonErrorCode.FORBIDDEN            // 无权限
CommonErrorCode.PARAM_INVALID        // 参数错误
CommonErrorCode.INTERNAL_ERROR       // 服务器异常
```

```java
// 业务模块错误码 (Nook 用 int 段位 + %s 格式化占位符)
@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {

    LOGIN_FAILED(3001, "邮箱或密码错误"),
    ACCOUNT_DISABLED(3002, "账户已禁用, 请联系管理员"),
    EMAIL_EXISTS(3003, "邮箱 %s 已被注册"),
    PASSWORD_TOO_WEAK(3004, "密码强度不足: 至少 8 位且含字母 + 数字"),
    OLD_PASSWORD_MISMATCH(3005, "原密码不正确"),
    MEMBER_NOT_FOUND(3006, "会员不存在"),
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

消息模板用 `%s` 占位 (`String.format` 兼容), 不是 `{}`. 抛异常: `throw new BusinessException(MemberErrorCode.EMAIL_EXISTS, email);`.

### 抛出异常

```java
import com.nook.common.web.exception.BusinessException;

throw new BusinessException(MemberErrorCode.LOGIN_FAILED);
throw new BusinessException(MemberErrorCode.REGISTER_EMAIL_EXISTS);
```

全局异常处理器 (在 `nook-framework` 已配) 会把 `BusinessException` 转 `Result.fail(code, msg)` 返回前端.

---

## 7. Controller

### 类与方法

```java
@RestController
@RequestMapping("/admin/<module>/<feature>")     // admin 路径; customer 路径前缀 /customer
@RequiredArgsConstructor
@Validated
public class SomeController {

    private final SomeService someService;

    @PostMapping("/create")
    public Result<String> create(@RequestBody @Valid SomeCreateReqVO reqVO) {
        return Result.ok(someService.create(reqVO).getId());
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody @Valid SomeUpdateReqVO reqVO) {
        someService.update(id, reqVO);
        return Result.ok();
    }

    @GetMapping("/page")
    public Result<PageResult<SomeRespVO>> page(@Valid SomePageReqVO reqVO) {
        PageResult<SomeEntity> page = someService.page(reqVO);
        return Result.ok(PageResult.of(page.getTotal(),
                SomeConvert.INSTANCE.convertList(page.getList())));
    }

    @GetMapping("/{id}")
    public Result<SomeRespVO> get(@PathVariable String id) {
        return Result.ok(SomeConvert.INSTANCE.convert(someService.findById(id)));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
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

    SomeRespVO convert(SomeEntity entity);

    List<SomeRespVO> convertList(List<SomeEntity> list);

    SomeEntity convert(SomeCreateReqVO vo);

    /** 提取 memberId 集合, 供 Controller 批量查关联. */
    default Set<String> extractMemberIds(List<SomeEntity> list) {
        return CollectionUtils.convertSet(list, SomeEntity::getMemberUserId);
    }

    /** 与会员名拼接. */
    default List<SomeRespVO> convertListWithMemberName(List<SomeEntity> list, Map<String, String> memberNameMap) {
        List<SomeRespVO> voList = convertList(list);
        for (SomeRespVO vo : voList) {
            vo.setMemberName(memberNameMap.get(vo.getMemberUserId()));
        }
        return voList;
    }
}
```

`default` 方法只接收纯数据 Map / List, **禁止**注入或传入 Service.

---

## 9. VO 命名

| 后缀 | 用途 | HTTP |
|---|---|---|
| `CreateReqVO` | 创建 | POST `/create` |
| `UpdateReqVO` | 更新 | PUT `/{id}` |
| `PageReqVO` | 分页查询 (含 `pageNo` / `pageSize`) | GET `/page` |
| `RespVO` | 标准响应 | 出参 |
| `SimpleRespVO` | 下拉等精简响应 | 出参 |

> 跟 datahub `SaveReqVO` 合并 Create / Update 的做法不同, Nook 现有代码 (`SystemUserCreateReqVO` / `SystemUserUpdateReqVO`) 分开. 沿用 Nook 现状.

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

---

## 10. Service

### 接口 + Impl 分离

```java
public interface SomeService {
    SomeEntity create(SomeCreateReqVO reqVO);
    void update(String id, SomeUpdateReqVO reqVO);
    void delete(String id, String operatorId);
    SomeEntity findById(String id);
    PageResult<SomeEntity> page(SomePageReqVO reqVO);
}

@Service
@RequiredArgsConstructor
public class SomeServiceImpl implements SomeService {

    private final SomeMapper someMapper;
    private final SomeValidator someValidator;
    // ...

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SomeEntity create(SomeCreateReqVO reqVO) {
        someValidator.validateUniqueName(null, reqVO.getName());

        SomeEntity entity = BeanUtils.toBean(reqVO, SomeEntity.class);
        someMapper.insert(entity);
        return entity;
    }
}
```

### 事务

- **写操作**必须 `@Transactional(rollbackFor = Exception.class)` (默认只回滚 `RuntimeException`)
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

### Validator (建议但非强制)

集中校验逻辑 (存在性 / 唯一性 / 业务前置) 到 `XxxValidator`, Service 调用. 参考 `SystemUserValidator`. 简单模块可省, 直接在 Service 内联校验.

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
        └── validator/                  # 可选
            └── XxxValidator.java
```

参考: `nook-module-agent` (已按此结构拆); `nook-module-system` / `nook-module-member` / `nook-module-node` 待逐步拆.

> 实体后缀: 新代码统一用 `XxxDO` (跟 mybatis-plus 习惯一致). 旧代码 (`SystemUser` / `XrayClient` 等) 沿用, 重构时顺手改。

---

## 12. 枚举

代码逻辑用枚举 (Nook 起步不引字典):

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

数据库存 `code` (整型), 实体字段用包装类型 (`Integer`).

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

### 类级 Javadoc

**一句话**职责说明. **禁止**用 `<ul>/<ol>` 列举多分支细节.

```java
/** 会员认证 Service —— 注册 / 登录 / 登出, 跟 sa-token member 体系对接. */
public class MemberAuthServiceImpl { ... }
```

### 方法级 Javadoc

**默认用标准格式**(说明 + `@param` + `@return`, void 可省 `@return`).
**只有底层 + 优先级低**的方法可以单行: `private` helper / 简单透传 / `@Override` 平凡方法.

```java
/**
 * 校验新密码强度: 至少 8 位且含字母 + 数字.
 *
 * @param password 明文密码
 */
private void validatePasswordStrength(String password) { ... }

/** 32 char hex sub_token. */
private String randomSubToken() { return RandomUtil.randomString(32); }
```

### 字段级 Javadoc

单行, 枚举值写清楚.

```java
/** 状态: 1=正常 2=禁用 */
private Integer status;
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

### package-info.java

仅作占位, **只保留 `package` 声明**, 不写 Javadoc 或 `@author`.

---

## 15. 提交前检查清单

**分层与命名**
- [ ] Controller 不含业务逻辑 (下沉到 Service)
- [ ] Convert 不注入 Service (仅接收纯数据 Map / List)
- [ ] Service 接口 + Impl 分离, `@Service` 标在 Impl
- [ ] VO 后缀 ∈ `CreateReqVO / UpdateReqVO / PageReqVO / RespVO / SimpleRespVO`
- [ ] Mapper 继承 `BaseMapper<T>`, default 方法封装 Wrapper, Service 不直接构造 Wrapper
- [ ] 跨模块调用走 `com.nook.biz.<module>.api.*`, 不直注其他模块的 Service / Mapper
- [ ] 关联拼接三步走: Convert 提取 ID → Controller 批量查 → Convert 拼接

**数据处理**
- [ ] 主键 `CHAR(32)`, 实体继承 `BaseEntity`, **不重复声明** id / createdAt / updatedAt
- [ ] 关联数据批量查 `loadXxxMap(ids)`, 不是逐条查
- [ ] Wrapper 更新必须显式 `.set(Entity::getUpdatedAt, LocalDateTime.now())`
- [ ] 先删后增 + 唯一键场景用 `physicalDelete`, SQL 写 XML 不用 `@Delete` 注解

**注解与校验**
- [ ] Controller 加 `@Validated`; 方法 `@RequestBody` 参数加 `@Valid`
- [ ] VO 必填加 `@NotBlank / @NotNull` 中文 message; 嵌套对象加 `@Valid`
- [ ] 注入用 `@RequiredArgsConstructor` + `private final`, **不用** `@Resource` / `@Autowired`

**事务、日志**
- [ ] 写操作 `@Transactional(rollbackFor = Exception.class)`, 读操作不加
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
- [ ] 类级 javadoc 一句话; 核心方法 `@param/@return` 标准格式; 简单方法单行

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

