import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

// vfonts: Naive UI 推荐的字体, 仅 ~5KB; 不引入也能跑, 但中文字号一致性会差一点
import 'vfonts/Lato.css'
import 'vfonts/FiraCode.css'

import '@/assets/styles/main.scss'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
