import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeAll, describe, expect, it, vi } from 'vitest'

import { SAMPLE_TREE } from '../fixtures'
import { elkEdgePath } from '../elkEdgePath'
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

  it('FR030_AT32_picks the clicked node and reports it only when its questions are requested', async () => {
    const onSelectKid = vi.fn()
    render(<KnowledgeGraph nodes={SAMPLE_TREE} onSelectKid={onSelectKid} selectedKid="NP2-906" />)

    expect(await nodeButton(/\(NP2-906\)/)).toHaveAttribute('aria-current', 'true')
    // The list filter's node starts picked, so its actions are offered straight away.
    expect(screen.getByRole('button', { name: 'Xem câu hỏi của NP2-906' })).toBeVisible()
    // fireEvent instead of userEvent: d3-zoom's mousedown handler needs a real window view jsdom lacks.
    fireEvent.click(await nodeButton(/\(NP2-913\)/))
    expect(onSelectKid).not.toHaveBeenCalled()
    expect(await nodeButton(/\(NP2-913\)/)).toHaveAttribute('aria-pressed', 'true')
    expect(
      within(screen.getByRole('group', { name: 'Nút đang chọn' })).getByText('NP2-913'),
    ).toBeVisible()

    fireEvent.click(screen.getByRole('button', { name: 'Xem câu hỏi của NP2-913' }))
    expect(onSelectKid).toHaveBeenCalledWith('NP2-913')
  })

  it('FR030_AT32_picks a node and opens its questions from the keyboard', async () => {
    const user = userEvent.setup()
    const onSelectKid = vi.fn()
    render(<KnowledgeGraph nodes={SAMPLE_TREE} onSelectKid={onSelectKid} />)

    ;(await nodeButton(/\(NP2-913\)/)).focus()
    await user.keyboard('{Enter}')
    screen.getByRole('button', { name: 'Xem câu hỏi của NP2-913' }).focus()
    await user.keyboard('{Enter}')
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

  it('FR031_AT33_shows only the prerequisite chain of the selected node and restores the filters', async () => {
    const user = userEvent.setup()
    render(<KnowledgeGraph nodes={SAMPLE_TREE} />)
    await nodeButton(/\(NP3-904\)/)
    expect(screen.queryByRole('button', { name: /Xem chuỗi/ })).not.toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Cấp HSK'), '2')
    fireEvent.click(await nodeButton(/\(NP2-913\)/))
    await user.click(screen.getByRole('button', { name: 'Xem chuỗi của NP2-913' }))

    expect(await screen.findByText(/^Đang xem chuỗi NP2-913 · 5 nút/)).toBeVisible()
    expect(await nodeButton(/\(NP1-914\)/)).toBeVisible()
    expect(await nodeButton(/\(NP3-904\)/)).toBeVisible()
    expect(screen.queryByRole('button', { name: /\(TV2-907\)/ })).not.toBeInTheDocument()
    expect(screen.getByLabelText('Cấp HSK')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Quay lại toàn sơ đồ' })).toHaveFocus()

    // Picking another chain node lets the editor walk further back.
    fireEvent.click(await nodeButton(/\(NP2-906\)/))
    await user.click(screen.getByRole('button', { name: 'Xem chuỗi của NP2-906' }))
    expect(await screen.findByText(/^Đang xem chuỗi NP2-906 · /)).toBeVisible()

    await user.click(screen.getByRole('button', { name: 'Quay lại toàn sơ đồ' }))
    expect(screen.getByLabelText('Cấp HSK')).toHaveValue('2')
    expect(await nodeButton(/\(TV2-907\)/)).toBeVisible()
    expect(screen.queryByRole('button', { name: /\(NP1-914\)/ })).not.toBeInTheDocument()
  })

  it('FR031_AT33_dims nodes that are not linked to the hovered node', async () => {
    render(<KnowledgeGraph nodes={SAMPLE_TREE} />)
    const hovered = await nodeButton(/\(NP2-913\)/)

    fireEvent.mouseEnter(hovered.closest('.react-flow__node')!)
    expect(await nodeButton(/\(TV2-907\)/)).toHaveClass('opacity-30')
    expect(await nodeButton(/\(NP3-904\)/)).not.toHaveClass('opacity-30')
    expect(hovered).not.toHaveClass('opacity-30')

    fireEvent.mouseLeave(hovered.closest('.react-flow__node')!)
    expect(await nodeButton(/\(TV2-907\)/)).not.toHaveClass('opacity-30')
  })

  it('FR031_AT33_labels each domain block when every domain is shown', async () => {
    const { container } = render(<KnowledgeGraph nodes={SAMPLE_TREE} />)
    await nodeButton(/\(NP3-904\)/)

    await waitFor(() =>
      expect(
        [...container.querySelectorAll('[data-domain-group]')].map((block) =>
          block.getAttribute('data-domain-group'),
        ),
      ).toEqual(['TV', 'NP', 'NL']),
    )
  })

  it('FR031_AT33_opens and closes the full-screen view', async () => {
    const user = userEvent.setup()
    render(<KnowledgeGraph nodes={SAMPLE_TREE} />)
    await nodeButton(/\(NP3-904\)/)
    const section = screen.getByRole('region', { name: 'Sơ đồ cây năng lực' })

    await user.click(screen.getByRole('button', { name: 'Toàn màn hình' }))
    expect(section).toHaveClass('fixed')
    expect(screen.getByRole('button', { name: 'Thoát toàn màn hình' })).toBeVisible()

    await user.keyboard('{Escape}')
    expect(section).not.toHaveClass('fixed')
    expect(screen.getByRole('button', { name: 'Toàn màn hình' })).toBeVisible()
  })

  it('FR030_AT32_shows empty states for an empty tree', () => {
    render(<KnowledgeGraph nodes={[]} />)

    const empty = screen.getByRole('heading', { name: 'Cây năng lực chưa có nút nào' })
    expect(within(empty.parentElement!).getByText(/Nhập nút và cạnh/)).toBeVisible()
  })
})

describe('elkEdgePath', () => {
  it('draws straight segments through the routed points', () => {
    expect(
      elkEdgePath([
        { x: 0, y: 10 },
        { x: 40, y: 10 },
        { x: 40, y: 60 },
        { x: 80, y: 60 },
      ]),
    ).toBe('M 0 10 L 40 10 L 40 60 L 80 60')
  })

  it('returns nothing when the route is missing so the caller can fall back', () => {
    expect(elkEdgePath(undefined)).toBeUndefined()
    expect(elkEdgePath([{ x: 0, y: 0 }])).toBeUndefined()
  })
})
