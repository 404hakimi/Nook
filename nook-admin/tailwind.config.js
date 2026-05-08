/** @type {import('tailwindcss').Config} */
export default {
  // useTheme 在 <html> 上切 data-theme="dark"; 留 attribute selector 让 dark: variant 能识别
  darkMode: ['class', '[data-theme="dark"]'],
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  // 关闭 preflight 避免与 Naive UI 内部的 button/input 复位互相覆盖;
  // 项目内仍可用所有 utility class (m-*, flex, grid 等), 不影响布局类需求
  corePlugins: { preflight: false },
  theme: {
    extend: {}
  },
  plugins: []
}
