import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeAll, describe, expect, it, vi } from 'vitest'

import { SAMPLE_TREE } from '../fixtures'
import KnowledgeGraph from './KnowledgeGraph'
import type { KnowledgeNode } from '@/features/question-bank/types'

beforeAll(() => {
  // React Flow measures its container; jsdom has neither ResizeObserver nor DOMMatrixReadOnly.
  globalThis.ResizeObserver ??= class {
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver
  globalThis.DOMMatrixReadOnly ??= class {
    m22 = 1
    constructor() {}
  } as unknown as typeof DOMMatrixReadOnly
})

async function nodeButton(name: RegExp) {
  return screen.findByRole('button', { name })
}

describe('KnowledgeGraph', () => {
  it('FR030_AT32_renders every node with an accessible label and the tree counts', async () => {
    render(<KnowledgeGraph nodes={SAMPLE_TREE} />)

    expect(await nodeButton(/^Câu xử lý đối tượng \(NP3-904\), HSK 3$/)).toBeVisible()
    expect(screen.getAllByRole('button', { name: /\([A-Z]{2}\d-\d{3}\)/ })).toHaveLength(7)
    expect(
      screen.getByText('7 nút · 5 cạnh tiên quyết · 2 cạnh hỗ trợ · 2 nút mồ côi', {
        exact: false,
      }),
    ).toBeVisible()
  })

  it('FR030_AT32_flags orphans and nodes without questions', async () => {
    const questionCounts = new Map([
      ['NP2-913', 0],
      ['NP3-904', 4],
    ])
    render(<KnowledgeGraph nodes={SAMPLE_TREE} questionCounts={questionCounts} />)

    expect(await nodeButton(/\(TV2-907\), HSK 2, Mồ côi$/)).toBeVisible()
    expect(await nodeButton(/\(NP2-913\), HSK 2, Chưa có câu$/)).toBeVisible()
    expect(await nodeButton(/\(NP3-904\), HSK 3$/)).toBeVisible()
    expect(screen.getByText(/1 nút chưa có câu/)).toBeVisible()
  })

  it('FR030_AT32_reports the clicked node', async () => {
    const onSelectKid = vi.fn()
    render(<KnowledgeGraph nodes={SAMPLE_TREE} onSelectKid={onSelectKid} selectedKid="NP2-906" />)

    expect(await nodeButton(/\(NP2-906\)/)).toHaveAttribute('aria-current', 'true')
    // fireEvent instead of userEvent: d3-zoom's mousedown handler needs a real window view jsdom lacks.
    fireEvent.click(await nodeButton(/\(NP2-913\)/))
    expect(onSelectKid).toHaveBeenCalledWith('NP2-913')
  })

  it('FR030_AT32_filters by domain and level', async () => {
    const user = userEvent.setup()
    render(<KnowledgeGraph nodes={SAMPLE_TREE} />)
    await nodeButton(/\(NP3-904\)/)

    await user.selectOptions(screen.getByLabelText('Miền'), 'NL')
    expect(await nodeButton(/\(NL3-906\)/)).toBeVisible()
    expect(screen.queryByRole('button', { name: /\(NP3-904\)/ })).not.toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Miền'), 'ALL')
    await user.selectOptions(screen.getByLabelText('Cấp HSK'), '1')
    expect(await nodeButton(/\(NP1-914\)/)).toBeVisible()
    expect(screen.queryByRole('button', { name: /\(NP2-906\)/ })).not.toBeInTheDocument()
  })

  it('FR030_AT32_opens large trees on a single domain', async () => {
    const big: KnowledgeNode[] = Array.from({ length: 61 }, (_, index) => ({
      domain: index < 31 ? 'CH' : 'TV',
      hskLevel: 1,
      kid: `${index < 31 ? 'CH' : 'TV'}1-${String(100 + index)}`,
      nameVi: `Nút ${index}`,
      nameZh: '示例',
      prereqKids: [],
      supportKids: [],
    }))
    render(<KnowledgeGraph nodes={big} />)

    expect(screen.getByLabelText('Miền')).toHaveValue('CH')
    expect(await screen.findByText(/^31 nút/)).toBeVisible()
  })

  it('FR030_AT32_shows empty states for an empty tree', () => {
    render(<KnowledgeGraph nodes={[]} />)

    const empty = screen.getByRole('heading', { name: 'Cây năng lực chưa có nút nào' })
    expect(within(empty.parentElement!).getByText(/Nhập nút và cạnh/)).toBeVisible()
  })
})
