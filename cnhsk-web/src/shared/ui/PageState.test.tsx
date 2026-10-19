import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { PageState, type PageStateValue } from '@/shared/ui/PageState'

function renderState(state: PageStateValue) {
  return render(
    <PageState
      empty={<div>Trạng thái rỗng</div>}
      error={<div>Trạng thái lỗi</div>}
      loading={<div>Trạng thái tải</div>}
      state={state}
    >
      <div>Có dữ liệu</div>
    </PageState>,
  )
}

describe('PageState', () => {
  it.each([
    ['loading', 'Trạng thái tải'],
    ['empty', 'Trạng thái rỗng'],
    ['error', 'Trạng thái lỗi'],
    ['ready', 'Có dữ liệu'],
  ] as const)('renders the %s branch', (state, expectedText) => {
    renderState(state)

    expect(screen.getByText(expectedText)).toBeVisible()
  })
})
