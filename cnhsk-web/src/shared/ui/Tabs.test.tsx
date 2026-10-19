import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'

import { Tabs, type TabItem } from '@/shared/ui/Tabs'

const items: TabItem[] = [
  { id: 'new', label: 'Từ mới', panel: 'Nội dung từ mới' },
  { id: 'practice', label: 'Luyện tập', panel: 'Nội dung luyện tập' },
  { disabled: true, id: 'locked', label: 'Không khả dụng', panel: 'Không hiển thị' },
]

function TabsHarness() {
  const [value, setValue] = useState('new')

  return <Tabs items={items} label="Các bước học" onChange={setValue} value={value} />
}

describe('Tabs', () => {
  it('links tabs to their panel and changes tabs with the keyboard', async () => {
    const user = userEvent.setup()
    render(<TabsHarness />)

    const firstTab = screen.getByRole('tab', { name: 'Từ mới' })
    expect(firstTab).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tabpanel')).toHaveTextContent('Nội dung từ mới')

    firstTab.focus()
    await user.keyboard('{ArrowRight}')

    expect(screen.getByRole('tab', { name: 'Luyện tập' })).toHaveFocus()
    expect(screen.getByRole('tab', { name: 'Luyện tập' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tabpanel')).toHaveTextContent('Nội dung luyện tập')
  })
})
