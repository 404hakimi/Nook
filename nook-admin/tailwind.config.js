/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {}
  },
  plugins: [require('daisyui')],
  daisyui: {
    // 内置主题：浅色 + 深色，可在 <html data-theme="dark"> 切换
    themes: ['light', 'dark'],
    darkTheme: 'dark',
    logs: false
  }
}
