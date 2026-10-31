import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import type { PronunciationStageSummary } from '../types'
import { PronunciationStageListView } from './PronunciationStageListView'

function stage(
  stageIndex: number,
  overrides: Partial<PronunciationStageSummary> = {},
): PronunciationStageSummary {
  return {
    activeAnswered: null,
    activeSessionId: null,
    bestAccuracy: null,
    description: `Mô tả tầng ${stageIndex}`,
    id: 100 + stageIndex,
    name: `Tầng mẫu ${stageIndex}`,
    passPercent: 80,
    playable: true,
    stageIndex,
    state: stageIndex === 1 ? 'AVAILABLE' : 'LOCKED',
    targetSounds: [`âm-${stageIndex}`],
    unitCount: 10,
    ...overrides,
  }
}

function renderView(stages: PronunciationStageSummary[]) {
  render(
    <MemoryRouter>
      <PronunciationStageListView onRetry={vi.fn()} stages={stages} state="ready" />
    </MemoryRouter>,
  )
}

describe('PronunciationStageListView', () => {
  it('FR017_hiện đủ ba trạng thái và lý do khoá bằng ngưỡng của tầng trước', () => {
    renderView([
      stage(1, { bestAccuracy: 87.5, passPercent: 82.5, state: 'COMPLETED' }),
      stage(2, { state: 'AVAILABLE' }),
      stage(3, { state: 'LOCKED' }),
    ])

    expect(screen.getByText('Hoàn thành')).toBeVisible()
    expect(screen.getByText('Đang học')).toBeVisible()
    expect(screen.getByText('Khoá')).toBeVisible()
    expect(screen.getByText('Hoàn thành tầng 2 với ≥ 80%')).toBeVisible()
    expect(screen.getByText('87,5%')).toBeVisible()
  })

  it('FR017_hiện Tiếp tục (x/y) khi tầng có lượt đang làm', () => {
    renderView([stage(1, { activeAnswered: 4, activeSessionId: 901 })])

    expect(screen.getByRole('link', { name: 'Tiếp tục (4/10)' })).toHaveAttribute(
      'href',
      '/learn/pronunciation/stages/1',
    )
  })

  it('FR017_khoá nút và hiện Đang cập nhật khi tầng chưa có bài', () => {
    renderView([stage(1, { playable: false, unitCount: 0 })])

    expect(screen.getByRole('button', { name: 'Đang cập nhật' })).toBeDisabled()
  })

  it('FR017_dùng đúng ngưỡng tầng k−1 thay vì ngưỡng tầng hiện tại', () => {
    renderView([stage(1, { passPercent: 82.5 }), stage(2, { passPercent: 95 })])

    expect(screen.getByText('Hoàn thành tầng 1 với ≥ 82,5%')).toBeVisible()
    expect(screen.queryByText(/với ≥ 95%/)).not.toBeInTheDocument()
  })

  it('FR017_thiếu tầng k−1 thì không mượn ngưỡng của dòng trước hay tạo thẻ giả', () => {
    renderView([stage(1, { passPercent: 82.5 }), stage(3, { passPercent: 95 })])

    expect(screen.getByText('Hoàn thành tầng 2 để mở tầng này')).toBeVisible()
    expect(screen.queryByText(/với ≥/)).not.toBeInTheDocument()
    expect(screen.queryByText('Tầng mẫu 2')).not.toBeInTheDocument()
    expect(
      screen
        .getAllByRole('heading', { level: 2 })
        .filter((heading) => heading.textContent?.startsWith('Tầng mẫu')),
    ).toHaveLength(2)
  })

  it('FR017_hiện mô tả âm luyện và đủ ba hành động theo dữ liệu API', () => {
    renderView([
      stage(1),
      stage(2, { activeAnswered: 4, activeSessionId: 901, state: 'AVAILABLE' }),
      stage(3, { bestAccuracy: 90, state: 'COMPLETED' }),
    ])

    expect(screen.getByText('Mô tả tầng 1')).toBeVisible()
    expect(within(screen.getByLabelText('Âm luyện tầng 1')).getByText('âm-1')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Bắt đầu' })).toHaveAttribute(
      'href',
      '/learn/pronunciation/stages/1',
    )
    expect(screen.getByRole('link', { name: 'Tiếp tục (4/10)' })).toHaveAttribute(
      'href',
      '/learn/pronunciation/stages/2',
    )
    expect(screen.getByRole('link', { name: 'Luyện lại' })).toHaveAttribute(
      'href',
      '/learn/pronunciation/stages/3',
    )
  })
})
