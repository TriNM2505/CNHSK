import { ESLint } from 'eslint'
import { describe, expect, it } from 'vitest'

import allowedPreviewImport from './lint-fixtures/allowed-preview-import.txt?raw'
import invalidClasses from './lint-fixtures/invalid-classes.txt?raw'
import restrictedPreviewImport from './lint-fixtures/restricted-preview-import.txt?raw'
import validClasses from './lint-fixtures/valid-classes.txt?raw'

const eslint = new ESLint()

async function lint(source: string, filePath: string) {
  const [result] = await eslint.lintText(source, { filePath })
  return result?.messages ?? []
}

describe('CNHSK ESLint rules', () => {
  it('rejects hex colors and mockup classes in JSX and class helpers', async () => {
    const messages = await lint(invalidClasses, 'src/test/__lint-fixture__.tsx')
    const hexLines = messages
      .filter(({ ruleId }) => ruleId === 'cnhsk/no-hex-color-class')
      .map(({ line }) => line)
    const mockupLines = messages
      .filter(({ ruleId }) => ruleId === 'cnhsk/no-mockup-class')
      .map(({ line }) => line)

    expect(hexLines).toEqual([6, 8, 11])
    expect(mockupLines).toEqual([6, 7, 8, 11])
  })

  it('allows valid Tailwind classes containing t segments', async () => {
    const messages = await lint(validClasses, 'src/test/__lint-fixture__.tsx')
    const cnhskMessages = messages.filter(({ ruleId }) => ruleId?.startsWith('cnhsk/'))

    expect(cnhskMessages).toEqual([])
  })

  it('rejects preview imports outside preview files', async () => {
    const messages = await lint(restrictedPreviewImport, 'src/test/__lint-fixture__.ts')

    expect(messages.map(({ ruleId }) => ruleId)).toContain('no-restricted-imports')
  })

  it('allows preview imports from preview files and the preview registry', async () => {
    const previewFileMessages = await lint(
      allowedPreviewImport,
      'src/features/example/components/Example.preview.ts',
    )
    const registryMessages = await lint(allowedPreviewImport, 'src/app/preview/registry.ts')

    expect(previewFileMessages).toEqual([])
    expect(registryMessages).toEqual([])
  })
})
