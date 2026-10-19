import type { Config } from 'tailwindcss'
import { createRequire } from 'node:module'

const loadModule = createRequire(import.meta.url)

export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  presets: [loadModule('./tailwind.preset.cjs')],
} satisfies Config
