import { describe, expect, it } from 'vitest'

import { formatPercentage } from './formatters'

describe('formatPercentage', () => {
  it('định dạng phần trăm theo vi-VN với tối đa hai chữ số thập phân', () => {
    expect(formatPercentage(87.5)).toBe('87,5%')
    expect(formatPercentage(87.567)).toBe('87,57%')
    expect(formatPercentage(80)).toBe('80%')
  })

  it('hiện Chưa có khi chưa có điểm', () => {
    expect(formatPercentage(null)).toBe('Chưa có')
    expect(formatPercentage(undefined)).toBe('Chưa có')
  })
})
