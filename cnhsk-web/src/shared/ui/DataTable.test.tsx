import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { DataTable, type DataTableColumn } from '@/shared/ui/DataTable'

interface UserRow {
  email: string
  id: number
  name: string
}

const rows: UserRow[] = [{ email: 'ba•••@gmail.com', id: 1, name: 'Người học mẫu' }]
const columns: DataTableColumn<UserRow>[] = [
  { header: 'Tên', id: 'name', render: (row) => row.name },
  { header: 'Email', id: 'email', render: (row) => row.email },
  { header: 'Cột phụ', hideOnMobile: true, id: 'extra', render: () => 'Chi tiết phụ' },
]

describe('DataTable', () => {
  it('renders an accessible desktop table and labelled mobile cards', () => {
    render(
      <DataTable
        caption="Danh sách người dùng"
        columns={columns}
        rowKey={(row) => row.id}
        rows={rows}
      />,
    )

    expect(screen.getByRole('table', { name: 'Danh sách người dùng' })).toBeVisible()

    const mobileCards = screen.getByRole('region', { name: 'Danh sách người dùng' })
    expect(within(mobileCards).getByText('Email:')).toBeVisible()
    expect(within(mobileCards).getByText('ba•••@gmail.com')).toBeVisible()
    expect(within(mobileCards).queryByText('Cột phụ:')).not.toBeInTheDocument()
    expect(within(mobileCards).queryByText('Chi tiết phụ')).not.toBeInTheDocument()
  })

  it('activates an interactive row with keyboard or mobile card click', () => {
    const onRowClick = vi.fn()
    render(
      <DataTable
        caption="Danh sách người dùng"
        columns={columns}
        getRowLabel={(row) => `Mở ${row.name}`}
        onRowClick={onRowClick}
        rowKey={(row) => row.id}
        rows={rows}
      />,
    )

    const desktopTable = screen.getByRole('table', { name: 'Danh sách người dùng' })
    fireEvent.keyDown(within(desktopTable).getByLabelText('Mở Người học mẫu'), { key: 'Enter' })
    fireEvent.click(screen.getByRole('button', { name: 'Mở Người học mẫu' }))

    expect(onRowClick).toHaveBeenCalledTimes(2)
    expect(onRowClick).toHaveBeenLastCalledWith(rows[0])
  })
})
