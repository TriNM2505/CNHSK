import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'
import { listAdminTopics, readAdminTopicCatalog, setTopicPrerequisites } from '../api/topics'
import type { AdminTopic, AdminTopicPage } from '../types'
import Scr067TopicPrerequisitesPage from './Scr067TopicPrerequisitesPage'

vi.mock('../api/topics', () => ({
  listAdminTopics: vi.fn(),
  readAdminTopicCatalog: vi.fn(),
  setTopicPrerequisites: vi.fn(),
}))
const root: AdminTopic = { id: 11, name: 'Chào hỏi', hskLevel: 1, prereqs: [], isRoot: true }
const extra: AdminTopic = { id: 12, name: 'Gia đình', hskLevel: 1, prereqs: [], isRoot: true }
const high: AdminTopic = { id: 52, name: 'Trường học', hskLevel: 3, prereqs: [], isRoot: true }
const target: AdminTopic = {
  id: 31,
  name: 'Giao tiếp',
  hskLevel: 2,
  prereqs: [root],
  isRoot: false,
}
const all = [target, root, extra, high]
const page = (items = all): AdminTopicPage => ({
  items,
  page: 0,
  size: 20,
  totalElements: items.length,
})
const picker = () => within(screen.getByRole('region', { name: 'Chọn tiên quyết' }))
const chosen = () => within(screen.getByRole('region', { name: 'Tiên quyết đã chọn' }))
const saveButton = () => screen.getByRole('button', { name: 'Lưu tiên quyết' })
const targetButton = async () =>
  within(await screen.findByRole('table', { name: 'Danh sách chủ đề' })).findByRole('button', {
    name: /Giao tiếp/,
  })
async function openTarget() {
  const user = userEvent.setup()
  render(<Scr067TopicPrerequisitesPage />)
  await user.click(await targetButton())
  await picker().findByRole('checkbox', { name: /Chào hỏi/ })
  return user
}
function failure(code: string, details?: unknown, status = 422) {
  return new ApiError({
    status,
    errorCode: code,
    message: 'Không thể lưu.',
    requestId: 'req-admin-topic',
    details,
  })
}

describe('SCR-067 topic prerequisites', () => {
  beforeEach(() => {
    vi.mocked(listAdminTopics).mockReset().mockResolvedValue(page())
    vi.mocked(readAdminTopicCatalog).mockReset().mockResolvedValue(all)
    vi.mocked(setTopicPrerequisites)
      .mockReset()
      .mockResolvedValue({ ...target, warnings: [] })
  })
  it('shows the fixed distinction, current prerequisites and self-selection guard', async () => {
    await openTarget()
    expect(
      screen.getByText('Đây là tiên quyết chủ đề (cổng 90%), khác tiên quyết KID của cây tri thức'),
    ).toBeVisible()
    expect(chosen().getByText('1/10 tiên quyết · Hiện có tiên quyết')).toBeVisible()
    expect(picker().getByRole('checkbox', { name: /Chào hỏi/ })).toBeChecked()
    expect(picker().getByRole('checkbox', { name: /Giao tiếp/ })).toBeDisabled()
  })
  it('sends ordered unique IDs and displays server level warnings after successful save', async () => {
    const user = await openTarget()
    const warnings = ['Tiên quyết Trường học (HSK 3) cao hơn chủ đề (HSK 2).']
    vi.mocked(setTopicPrerequisites).mockResolvedValue({
      ...target,
      prereqs: [root, high, extra],
      warnings,
    })
    await user.click(picker().getByRole('checkbox', { name: /Trường học/ }))
    await user.click(picker().getByRole('checkbox', { name: /Gia đình/ }))
    await user.click(saveButton())
    expect(setTopicPrerequisites).toHaveBeenCalledTimes(1)
    expect(setTopicPrerequisites).toHaveBeenCalledWith(31, [11, 52, 12])
    expect(await screen.findByText('Đã lưu tiên quyết chủ đề.')).toBeVisible()
    expect(screen.getByText(warnings[0]!)).toBeVisible()
  })
  it('allows an empty replacement to turn the target into a root', async () => {
    const user = await openTarget()
    vi.mocked(setTopicPrerequisites).mockResolvedValue({
      ...target,
      prereqs: [],
      isRoot: true,
      warnings: [],
    })
    await user.click(chosen().getByRole('button', { name: 'Bỏ tiên quyết Chào hỏi' }))
    await user.click(saveButton())
    expect(setTopicPrerequisites).toHaveBeenCalledWith(31, [])
    expect(await screen.findByText('0/10 tiên quyết · Hiện là chủ đề gốc')).toBeVisible()
  })
  it('preserves choices across candidate pages, search and HSK filters without fetching the whole catalog', async () => {
    vi.mocked(listAdminTopics).mockImplementation(async (filters) => {
      if (filters?.page === 1) return { items: [high], page: 1, size: 20, totalElements: 21 }
      if (filters?.q || filters?.hsk) return page([high])
      return { ...page(), totalElements: 21 }
    })
    const user = await openTarget()
    await user.click(picker().getByRole('checkbox', { name: /Gia đình/ }))
    await user.click(within(picker().getByRole('navigation')).getByRole('button', { name: 'Sau' }))
    await user.click(await picker().findByRole('checkbox', { name: /Trường học/ }))
    await user.type(picker().getByRole('searchbox', { name: 'Tìm theo tên' }), 'Trường')
    await user.selectOptions(picker().getByRole('combobox', { name: 'Cấp HSK' }), '3')
    await waitFor(() =>
      expect(listAdminTopics).toHaveBeenLastCalledWith(
        { page: 0, q: 'Trường', hsk: 3 },
        expect.any(AbortSignal),
      ),
    )
    await user.click(saveButton())
    expect(setTopicPrerequisites).toHaveBeenCalledWith(31, [11, 12, 52])
    expect(readAdminTopicCatalog).not.toHaveBeenCalled()
  })
  it('limits selection to ten and lets a removed choice free one slot', async () => {
    const ten = Array.from({ length: 10 }, (_, index) => ({
      ...root,
      id: index + 100,
      name: `Tiên quyết ${index + 1}`,
    }))
    vi.mocked(listAdminTopics).mockResolvedValue(page([{ ...target, prereqs: ten }, ...ten, extra]))
    const user = await openTargetWithMany()
    expect(picker().getByRole('checkbox', { name: /Gia đình/ })).toBeDisabled()
    await user.click(chosen().getByRole('button', { name: 'Bỏ tiên quyết Tiên quyết 1' }))
    await user.click(picker().getByRole('checkbox', { name: /Gia đình/ }))
    await user.click(saveButton())
    expect(setTopicPrerequisites).toHaveBeenCalledWith(31, [
      ...ten.slice(1).map(({ id }) => id),
      12,
    ])
  })
  it('shows the closed cycle in server order and resolves a name outside the visible page', async () => {
    const user = await openTarget()
    vi.mocked(readAdminTopicCatalog).mockResolvedValue([
      ...all,
      { ...root, id: 99, name: 'Chủ đề ngoài trang' },
    ])
    vi.mocked(setTopicPrerequisites).mockRejectedValue(
      failure('PREREQUISITE_CYCLE', { path: [31, 99, 11, 31] }),
    )
    await user.click(saveButton())
    expect(
      await screen.findByText('Đường vòng: Giao tiếp → Chủ đề ngoài trang → Chào hỏi → Giao tiếp'),
    ).toBeVisible()
    expect(readAdminTopicCatalog).toHaveBeenCalledTimes(1)
    expect(picker().getByRole('checkbox', { name: /Chào hỏi/ })).toBeChecked()
    expect(setTopicPrerequisites).toHaveBeenCalledTimes(1)
  })
  it('shows a known self-cycle without reading more pages', async () => {
    const user = await openTarget()
    vi.mocked(setTopicPrerequisites).mockRejectedValue(
      failure('PREREQUISITE_CYCLE', { path: [31, 31] }),
    )
    await user.click(saveButton())
    expect(await screen.findByText('Đường vòng: Giao tiếp → Giao tiếp')).toBeVisible()
    expect(readAdminTopicCatalog).not.toHaveBeenCalled()
  })
  it('keeps the draft on NO_ROOT and never retries the write automatically', async () => {
    const user = await openTarget()
    vi.mocked(setTopicPrerequisites).mockRejectedValue(failure('PREREQUISITE_NO_ROOT'))
    await user.click(picker().getByRole('checkbox', { name: /Gia đình/ }))
    await user.click(saveButton())
    expect(await screen.findByRole('alert')).toHaveTextContent('cần ít nhất một chủ đề gốc')
    expect(picker().getByRole('checkbox', { name: /Gia đình/ })).toBeChecked()
    expect(setTopicPrerequisites).toHaveBeenCalledTimes(1)
  })
  it('reloads after 404, drops missing prerequisite IDs and waits for the editor to save again', async () => {
    const user = await openTarget()
    vi.mocked(setTopicPrerequisites).mockRejectedValue(failure('TOPIC_NOT_FOUND', undefined, 404))
    vi.mocked(readAdminTopicCatalog).mockResolvedValue([{ ...target, prereqs: [] }, extra, high])
    await user.click(saveButton())
    expect(await screen.findByRole('alert')).toHaveTextContent('Đã tải lại; hãy kiểm tra lựa chọn')
    expect(chosen().getByText(/0\/10 tiên quyết/)).toBeVisible()
    expect(setTopicPrerequisites).toHaveBeenCalledTimes(1)
    expect(readAdminTopicCatalog).toHaveBeenCalledTimes(1)
  })
  it('shows a 404 even when the selected target has disappeared', async () => {
    const user = await openTarget()
    vi.mocked(setTopicPrerequisites).mockRejectedValue(failure('TOPIC_NOT_FOUND', undefined, 404))
    vi.mocked(readAdminTopicCatalog).mockResolvedValue([root, extra])
    await user.click(saveButton())
    expect(await screen.findByRole('alert')).toHaveTextContent('Dữ liệu chủ đề đã thay đổi')
    expect(screen.queryByRole('button', { name: 'Lưu tiên quyết' })).not.toBeInTheDocument()
  })
  it('reads after a network failure and does not retry PUT', async () => {
    const user = await openTarget()
    vi.mocked(setTopicPrerequisites).mockRejectedValue(failure('NETWORK_ERROR', undefined, 0))
    await user.click(saveButton())
    expect(await screen.findByRole('alert')).toHaveTextContent('Đã đọc lại dữ liệu')
    expect(readAdminTopicCatalog).toHaveBeenCalledTimes(1)
    expect(setTopicPrerequisites).toHaveBeenCalledTimes(1)
  })
  it('prevents duplicate writes and disables editing until the response arrives', async () => {
    const user = await openTarget()
    let resolve!: (value: AdminTopic) => void
    vi.mocked(setTopicPrerequisites).mockReturnValue(
      new Promise((done) => {
        resolve = done
      }),
    )
    await user.dblClick(saveButton())
    expect(setTopicPrerequisites).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'Đang lưu…' })).toBeDisabled()
    expect(picker().getByRole('checkbox', { name: /Gia đình/ })).toBeDisabled()
    await act(async () => resolve({ ...target, warnings: [] }))
    expect(await screen.findByText('Đã lưu tiên quyết chủ đề.')).toBeVisible()
  })
  it('renders an empty list without fabricating topics', async () => {
    vi.mocked(listAdminTopics).mockResolvedValue(page([]))
    render(<Scr067TopicPrerequisitesPage />)
    expect(await screen.findByText('Không có chủ đề phù hợp')).toBeVisible()
    expect(setTopicPrerequisites).not.toHaveBeenCalled()
  })
  it('retries a failed GET on request', async () => {
    vi.mocked(listAdminTopics)
      .mockRejectedValueOnce(failure('SERVICE_UNAVAILABLE', undefined, 503))
      .mockResolvedValue(page())
    const user = userEvent.setup()
    render(<Scr067TopicPrerequisitesPage />)
    await user.click(await screen.findByRole('button', { name: /Thử lại/ }))
    expect(await targetButton()).toBeVisible()
  })
})

async function openTargetWithMany() {
  const user = userEvent.setup()
  render(<Scr067TopicPrerequisitesPage />)
  await user.click(await targetButton())
  await picker().findByRole('checkbox', { name: /Gia đình/ })
  return user
}
