import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { GradingScoreBreakdown } from './GradingScoreBreakdown'

describe('GradingScoreBreakdown', () => {
  it('shows each rubric criterion with its level description and the confirmed error codes', () => {
    render(
      <GradingScoreBreakdown
        mode="SPEAKING"
        result={{
          score: 67,
          rubric: 'R_NOI',
          scores: { C1: 2, C2: 2, C3: 2, C4: 2, C5: 2 },
          errors: [
            {
              code: 'E-NP-06',
              nameVi: 'Thiếu bổ ngữ kết quả',
              severity: 2,
              fixHint: 'Thêm 完 sau động từ',
            },
          ],
          feedback: 'Tốt',
          corrections: [],
        }}
      />,
    )
    expect(screen.getByText('Chấm theo R-NÓI · thang 0–3')).toBeVisible()
    expect(screen.getByText('C5 · Trôi chảy, liên kết')).toBeVisible()
    expect(screen.getAllByText('2/3')).toHaveLength(5)
    expect(screen.getByText('Dừng ngắn, tự sửa được; có từ nối cơ bản')).toBeVisible()
    expect(screen.getByText('Thiếu bổ ngữ kết quả')).toBeVisible()
    expect(screen.getByText('Mức Vừa')).toBeVisible()
    expect(screen.getByText('Cách sửa: Thêm 完 sau động từ')).toBeVisible()
  })

  it('keeps showing legacy four-mark grades', () => {
    render(
      <GradingScoreBreakdown
        mode="ESSAY"
        result={{
          score: 78,
          criteria: { grammar: 20, vocabulary: 18, coherence: 21, writing: 19 },
          feedback: 'x',
          corrections: [],
        }}
      />,
    )
    expect(screen.getByText('Ngữ pháp')).toBeVisible()
    expect(screen.getByText('20/25')).toBeVisible()
  })
})
