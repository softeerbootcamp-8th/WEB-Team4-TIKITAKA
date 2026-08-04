import { defineConfig, loadEnv } from 'vite'
import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'
import tailwindcss from '@tailwindcss/vite'

const API_PATH_PREFIX = '/api'
const DEFAULT_DEV_API_TARGET = 'http://localhost:8080'
const ENV_PREFIX = 'VITE_'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), ENV_PREFIX)

  return {
    plugins: [
      react(),
      babel({ presets: [reactCompilerPreset()] }),
      tailwindcss(),
    ],
    /*
     * 개발 서버에서 /api 요청을 로컬 백엔드로 넘긴다.
     * 같은 오리진으로 보내므로 CORS 설정 없이 세션 쿠키가 그대로 붙는다.
     * 백엔드 주소가 다르면 .env.local에 VITE_DEV_API_PROXY_TARGET을 지정한다.
     */
    server: {
      proxy: {
        [API_PATH_PREFIX]: {
          target: env.VITE_DEV_API_PROXY_TARGET ?? DEFAULT_DEV_API_TARGET,
          changeOrigin: true,
        },
      },
    },
  }
})
