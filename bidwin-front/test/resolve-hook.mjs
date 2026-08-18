/*
 * 소스는 확장자 없이 상대 경로를 import 한다(`./constants`). 번들러(Vite)는 이를 해석하지만
 * node의 ESM 해석기는 하지 않아, 테스트에서 그 모듈들을 그대로 불러올 수 없다.
 * 소스를 테스트 사정에 맞춰 고치지 않도록, 테스트를 돌릴 때만 `.ts`를 붙여 한 번 더 찾아본다.
 */
import { registerHooks } from 'node:module'

registerHooks({
  resolve(specifier, context, nextResolve) {
    try {
      return nextResolve(specifier, context)
    } catch (error) {
      if (!specifier.startsWith('.')) throw error
      return nextResolve(`${specifier}.ts`, context)
    }
  },
})
