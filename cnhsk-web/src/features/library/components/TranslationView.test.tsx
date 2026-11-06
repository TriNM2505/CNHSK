import { fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import type { TranslateResult } from '../types'
import { TranslationView, type TranslationViewProps } from './TranslationView'

function props(overrides: Partial<TranslationViewProps> = {}): TranslationViewProps {
  return {
    draft: { text: '我读书。', from: 'ZH', to: 'VI' },
    pending: false,
    onTextChange: vi.fn(),
    onDirectionChange: vi.fn(),
    onSubmit: vi.fn(),
    onOpenLexeme: vi.fn(),
    ...overrides,
  }
}
const result: TranslateResult = {
  translated: 'Tôi đọc sách.',
  segments: [],
  pinyin: null,
  cached: false,
  consumption: null,
}
function view(value: TranslationViewProps) {
  return render(
    <MemoryRouter>
      <TranslationView {...value} />
    </MemoryRouter>,
  )
}

describe('TranslationView', () => {
  it.each([
    { cached: true, consumption: null, label: 'Từ bộ nhớ đệm — không trừ lượt' },
    { cached: false, consumption: 'QUOTA', label: 'Đã dùng 1 lượt miễn phí.' },
    { cached: false, consumption: 'CREDIT', label: 'Đã dùng điểm.' },
    { cached: false, consumption: null, label: null },
  ] as const)(
    'shows cached=$cached consumption=$consumption without inferring amounts',
    ({ cached, consumption, label }) => {
      view(props({ result: { ...result, cached, consumption } }))
      if (label) expect(screen.getByRole('status')).toHaveTextContent(label)
      else expect(screen.queryByRole('status')).not.toBeInTheDocument()
      expect(screen.getByText('Tôi đọc sách.')).toBeVisible()
      expect(screen.queryByText('Pinyin của nội dung tiếng Trung')).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: /^Tra từ/ })).not.toBeInTheDocument()
    },
  )

  it('keeps translated text with empty segments and shows pinyin independently when present', () => {
    view(props({ result: { ...result, pinyin: 'wǒ dú shū' } }))
    expect(screen.getByText('Tôi đọc sách.')).toBeVisible()
    expect(screen.getByText('wǒ dú shū')).toBeVisible()
    expect(screen.queryByText('Tra từ trong nội dung tiếng Trung')).not.toBeInTheDocument()
    expect(screen.queryByText('Không thể tải dữ liệu')).not.toBeInTheDocument()
  })

  it('renders HTML and script payloads as text and only linked segments as buttons', () => {
    const value = props({
      result: {
        ...result,
        translated: '<script>alert(1)</script><b>HTML</b>',
        pinyin: '<img src=x onerror=alert(1)>',
        segments: [
          { text: '<b>字</b>', lexemeId: 41 },
          { text: '<script>未知</script>', lexemeId: null },
        ],
      },
    })
    const { container } = view(value)
    const region = screen.getByRole('region', { name: 'Kết quả dịch' })
    expect(within(region).getByText('<script>alert(1)</script><b>HTML</b>')).toBeVisible()
    expect(container.querySelector('script,img,b')).toBeNull()
    expect(within(region).getAllByRole('button')).toHaveLength(1)
    fireEvent.click(screen.getByRole('button', { name: 'Tra từ <b>字</b>' }))
    expect(value.onOpenLexeme).toHaveBeenCalledWith(41)
  })

  it('renders empty and loading states with disabled controls', () => {
    const value = props({ draft: { text: '', from: 'ZH', to: 'VI' } })
    const { rerender } = view(value)
    expect(screen.getByRole('heading', { name: 'Chưa có bản dịch' })).toBeVisible()
    expect(screen.getByText('0/2.000 ký tự')).toBeVisible()
    rerender(
      <MemoryRouter>
        <TranslationView {...value} pending />
      </MemoryRouter>,
    )
    expect(screen.getByRole('status')).toHaveTextContent('Đang dịch nội dung…')
    expect(screen.getByRole('textbox')).toBeDisabled()
    expect(screen.getByRole('combobox')).toBeDisabled()
    expect(screen.queryByRole('heading', { name: 'Chưa có bản dịch' })).not.toBeInTheDocument()
  })
})
