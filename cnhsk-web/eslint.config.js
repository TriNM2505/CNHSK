import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'

import cnhskPlugin from './eslint-rules.js'

export default tseslint.config(
  { ignores: ['dist', 'coverage', 'src/test/lint-fixtures/**'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
    plugins: {
      cnhsk: cnhskPlugin,
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'cnhsk/no-hex-color-class': 'error',
      'cnhsk/no-mockup-class': 'error',
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['**/*.preview', '*.preview'],
              message: 'Chỉ file preview được phép import dữ liệu *.preview.',
            },
          ],
        },
      ],
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
    },
  },
  {
    files: ['**/*.preview.ts', 'src/app/preview/**'],
    rules: {
      'no-restricted-imports': 'off',
    },
  },
)
