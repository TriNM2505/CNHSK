import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { HskSkillsPanel } from './HskSkillsPanel'

describe('HskSkillsPanel', () => {
  it('shows good nodes and mastery per skill, keeps grey nodes apart and summarises domains', () => {
    render(
      <MemoryRouter>
        <HskSkillsPanel
          mastery={{
            hskLevel: 3,
            skills: [
              { code: 'LISTENING', nodes: 20, good: 5, missing: 12, percent: 64 },
              { code: 'SPEAKING', nodes: 18, good: 0, missing: 18, percent: null },
              { code: 'READING', nodes: 40, good: 10, missing: 20, percent: 71 },
              { code: 'WRITING', nodes: 30, good: 2, missing: 25, percent: 40 },
            ],
            domains: [
              { code: 'NP', nodes: 22, good: 14, missing: 4, percent: 80 },
              { code: 'VB', nodes: 0, good: 0, missing: 0, percent: null },
            ],
          }}
        />
      </MemoryRouter>,
    )
    expect(screen.getByText('5/20 nút tốt')).toBeVisible()
    expect(screen.getByRole('progressbar', { name: 'Mức nắm vững 64%' })).toHaveAttribute(
      'aria-valuenow',
      '64',
    )
    expect(screen.getByText('Chưa có bài')).toBeVisible()
    expect(screen.getByText('18 nút cần làm thêm bài')).toBeVisible()
    expect(screen.getByText(/Ngữ pháp 14\/22/)).toBeVisible()
    expect(screen.queryByText(/Văn bản/)).not.toBeInTheDocument()
    expect(screen.getByText('Chuẩn HSK 3.0 cấp 1–3 yêu cầu đủ nghe, nói, đọc, viết.')).toBeVisible()
    expect(screen.getByRole('link', { name: /Nhiệm vụ viết/ })).toHaveAttribute(
      'href',
      '/learn/writing',
    )
  })
})
