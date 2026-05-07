# nook-admin

Nook 后台管理端（Web）。

## 技术栈

- Vue 3.5 + TypeScript + Vite 5
- Vue Router 4 + Pinia 2
- **Tailwind CSS 3 + DaisyUI 4**（纯 CSS 类，按需即写）
- Lucide Vue Next（图标）
- Axios

## 设计取舍

DaisyUI 只提供样式，不带交互行为。因此：

| 功能 | 实现方式 |
|---|---|
| Toast / 消息提示 | `composables/useToast.ts` 模块级单例 + `components/ToastContainer.vue` 全局挂载 |
| Modal / 确认弹窗 | 当前用原生 `confirm()`；后续按 DaisyUI `modal` + 自研 useConfirm |
| 表单校验 | 手写 validate 函数（字段少时简单可控） |
| 主题切换 | `<html data-theme="light\|dark">`，配合 DaisyUI 内置主题 |

## 目录结构

```
src/
├── api/                    后端接口，按业务模块分子目录
│   ├── request.ts          axios 实例 + 拦截器
│   ├── types.ts            ApiResult 等公共类型
│   ├── system/auth.ts
│   ├── member/business/resource/xray/monitor/    预留
├── assets/styles/main.scss 全局样式 + Tailwind directives
├── components/
│   └── ToastContainer.vue  全局 toast 容器
├── composables/
│   └── useToast.ts         toast 调用入口
├── constants/              前端常量 (预留)
├── layouts/
│   └── AdminLayout.vue     侧边抽屉(drawer) + 顶栏(navbar)
├── plugins/                第三方插件初始化 (预留)
├── router/index.ts
├── stores/user.ts          Pinia: 登录态 + 当前用户
├── types/utils/            预留
├── views/
│   ├── auth/Login.vue
│   ├── dashboard/Index.vue
│   ├── system/business/resource/xray/monitor/    预留
│   └── error/NotFound.vue
├── App.vue
└── main.ts
```

## 路径别名

`@/*` → `src/*`（见 `vite.config.ts` 与 `tsconfig.app.json`）。
import 一律用 `@/...`，禁止相对路径上跳两级及以上 (`../../`)。

## 鉴权约定

- 登录后 sa-token 写到 `localStorage[nook-admin-token]`
- 每次请求 axios 拦截器自动塞 `Authorization` 头（与后端 `application.yml` 中 `sa-token.token-name` 一致）
- 收到 `code=1002`（后端 `CommonErrorCode.UNAUTHORIZED`）时拦截器自动清 token 并跳 `/login`
- 路由守卫：受保护页面如未登录跳登录；如有 token 但无用户信息则调一次 `/admin/system/auth/me`

## 主题

`index.html` 上 `<html data-theme="light">` 切到 `dark` 即可换主题。
扩展自定义主题：在 `tailwind.config.js` 的 `daisyui.themes` 数组配置。

## 开发

```bash
cd nook-admin
npm i
npm run dev
```

默认监听 `http://localhost:5173`，`/api/**` 通过 Vite proxy 转发到后端 `http://localhost:8080`。
