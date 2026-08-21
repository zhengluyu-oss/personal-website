import {ConfigEnv, defineConfig, loadEnv} from 'vite'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import {ElementPlusResolver} from 'unplugin-vue-components/resolvers'
import { visualizer } from 'rollup-plugin-visualizer'
import vue from '@vitejs/plugin-vue'
// 引入svg需要用到插件
import {createSvgIconsPlugin} from 'vite-plugin-svg-icons'
import path from 'path'
import tailwindcss from 'tailwindcss'

// https://vitejs.dev/config/
export default defineConfig(({ mode }: ConfigEnv) => {
    const env = loadEnv(mode, process.cwd())
    const analyzeBundle = env.VITE_ANALYZE === 'true'
    return {
        plugins: [
            vue(),
            createSvgIconsPlugin({
                // 指定需要缓存的图标文件夹
                iconDirs: [path.resolve(process.cwd(), 'src/assets/icons')],
                // 指定symbolId格式
                symbolId: 'icon-[dir]-[name]',
            }),
            AutoImport({
                imports: ['vue', 'vue-router', 'pinia'],
                resolvers: [ElementPlusResolver()],
                dts: mode === 'development' ? "src/types/auto-imports.d.ts" : false,
            }),
            Components({
                resolvers: [ElementPlusResolver()],
                dts: mode === 'development' ? "src/types/components.d.ts" : false,
            }),
            // 打包体积分析
            analyzeBundle && visualizer({
                open: false,
                filename: 'visualizer.html'
            })
        ],
        resolve: {
            alias: {
                "@": path.resolve("./src") // 相对路径别名配置，使用 @ 代替 src
            }
        },
        css: {
            preprocessorOptions: {
                scss: {
                    javascriptEnabled: true,
                    additionalData: '@import "./src/styles/variable.scss";',
                },
            },
            postcss: {
                plugins: [
                    tailwindcss,
                ]
            }
        },
        build: {
            manifest: true,
            rollupOptions: {
                // 配置打包文件分类输出
                output: {
                    chunkFileNames: 'js/[name]-[hash].js', // 引入文件名的名称
                    entryFileNames: 'js/[name]-[hash].js', // 包的入口文件名称
                    assetFileNames: '[ext]/[name]-[hash].[ext]', // 资源文件像 字体，图片等
                }
            }
        },
        server: (() => {
            const proxy: Record<string, { target: string; changeOrigin: boolean; rewrite: (path: string) => string }> = {
                '/api': {
                    target: env.VITE_SERVE,
                    changeOrigin: true,
                    rewrite: (path) => path.replace(/^\/api/, '')
                }
            }
            // 无音乐后端时不注册 /wapi，避免空 target 导致开发服 500
            if (env.VITE_MUSIC_SERVE) {
                proxy['/wapi'] = {
                    target: env.VITE_MUSIC_SERVE,
                    changeOrigin: true,
                    rewrite: (path) => path.replace(/^\/wapi/, '')
                }
            }
            return {
                port: 99,
                host: '0.0.0.0',
                proxy
            }
        })()
    }
})
