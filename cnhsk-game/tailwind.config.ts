import { createRequire } from 'node:module'
import type { Config } from 'tailwindcss'

const loadModule = createRequire(import.meta.url)

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  presets: [loadModule('../cnhsk-web/tailwind.preset.cjs')],
} satisfies Config
