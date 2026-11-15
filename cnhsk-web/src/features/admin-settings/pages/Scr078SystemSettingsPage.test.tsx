import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api'
import {
  getAiUsage,
  getSettingHistory,
  listSettings,
  resetSetting,
  updateSetting,
} from '../api/settings'
import { historyFixture, settingFixture, settingsFixtures, usageFixture } from '../test-fixtures'
import type { AiUsage, SettingHistoryPage, SettingItem } from '../types'
import Scr078SystemSettingsPage from './Scr078SystemSettingsPage'

vi.mock('../api/settings', () => ({
  getAiUsage: vi.fn(),
  getSettingHistory: vi.fn(),
  listSettings: vi.fn(),
  resetSetting: vi.fn(),
  updateSetting: vi.fn(),
}))
const key = settingFixture.key
const row = (id = key) => within(screen.getByRole('article', { name: id }))
const failure = (status: number, code = 'SETTING_OUT_OF_RANGE') =>
  new ApiError({
    status,
    errorCode: code,
    message: 'Không thể áp dụng giá trị.',
    requestId: 'settings-1',
  })
function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}
async function open(mode: 'edit' | 'reset' = 'edit') {
  render(<Scr078SystemSettingsPage />)
  fireEvent.click(
    await screen.findByRole('button', {
      name: `${mode === 'edit' ? 'Sửa' : 'Về mặc định'} ${key}`,
    }),
  )
}
function confirm(value = '30') {
  const input = screen.queryByLabelText('Giá trị mới', { exact: false })
  if (input) fireEvent.change(input, { target: { value } })
  fireEvent.change(screen.getByLabelText('Lý do', { exact: false }), {
    target: { value: 'Điều chỉnh hạn mức.' },
  })
  fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục' }))
  fireEvent.click(screen.getByRole('button', { name: 'Xác nhận lưu' }))
}
beforeEach(() => {
  vi.mocked(listSettings).mockReset().mockResolvedValue(settingsFixtures)
  vi.mocked(getAiUsage).mockReset().mockResolvedValue(usageFixture)
  vi.mocked(getSettingHistory).mockReset().mockResolvedValue(historyFixture)
  vi.mocked(updateSetting)
    .mockReset()
    .mockResolvedValue({ ...settingFixture, currentValue: 30, overridden: true })
  vi.mocked(resetSetting).mockReset().mockResolvedValue(settingFixture)
})
describe('SCR-078 settings page', () => {
  it('groups every returned key including new catalog groups and preserves zero', async () => {
    render(<Scr078SystemSettingsPage />)
    expect(await screen.findByRole('region', { name: 'AI sinh câu' })).toBeVisible()
    expect(row('ai-generate.max-batches-per-run').getByText('0')).toBeVisible()
    expect(screen.getAllByRole('article')).toHaveLength(settingsFixtures.length)
    expect(row('ai-generate.max-batches-per-run').getByText(/Quản trị nội dung 示例/)).toBeVisible()
  })
  it.each(['-1', '10001', '', '1.5'])(
    'blocks invalid value %s and blank reason before HTTP',
    async (value) => {
      await open()
      fireEvent.change(screen.getByLabelText('Giá trị mới', { exact: false }), {
        target: { value },
      })
      fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục' }))
      expect(updateSetting).not.toHaveBeenCalled()
      expect(resetSetting).not.toHaveBeenCalled()
      expect(screen.queryByRole('button', { name: 'Xác nhận lưu' })).not.toBeInTheDocument()
    },
  )
  it('saves zero only after explicit confirmation and refreshes authoritative rows', async () => {
    await open()
    const updated = { ...settingFixture, currentValue: 0, overridden: true }
    vi.mocked(updateSetting).mockResolvedValue(updated)
    vi.mocked(listSettings).mockResolvedValue(
      settingsFixtures.map((item) => (item.key === key ? updated : item)),
    )
    confirm('0')
    expect(await screen.findByText('Đã lưu cài đặt.')).toBeVisible()
    expect(updateSetting).toHaveBeenCalledWith(key, 0, 'Điều chỉnh hạn mức.')
    expect(row().getByText('0')).toBeVisible()
    expect(listSettings).toHaveBeenCalledTimes(2)
  })
  it('keeps draft and reason on 422 without retrying or replacing inputs', async () => {
    vi.mocked(updateSetting).mockRejectedValue(failure(422))
    await open()
    confirm()
    expect(await screen.findByText('Không thể áp dụng giá trị.')).toBeVisible()
    expect(screen.getByLabelText('Giá trị mới', { exact: false })).toHaveValue('30')
    expect(screen.getByLabelText('Lý do', { exact: false })).toHaveValue('Điều chỉnh hạn mức.')
    expect(updateSetting).toHaveBeenCalledOnce()
    expect(listSettings).toHaveBeenCalledOnce()
  })
  it('blocks duplicate writes and changing keys while a mutation is pending', async () => {
    const write = deferred<SettingItem>()
    vi.mocked(updateSetting).mockReturnValue(write.promise)
    await open()
    confirm()
    fireEvent.submit(screen.getByRole('form', { name: `Thay đổi ${key}` }))
    expect(updateSetting).toHaveBeenCalledOnce()
    expect(screen.getByRole('button', { name: 'Sửa grading.min-hanzi' })).toBeDisabled()
    await act(async () => write.resolve({ ...settingFixture, currentValue: 30 }))
  })
  it('resets an already-default setting twice, retaining the reason in both DELETE calls', async () => {
    await open('reset')
    confirm()
    await screen.findByText('Đã đưa cài đặt về mặc định.')
    fireEvent.click(screen.getByRole('button', { name: `Về mặc định ${key}` }))
    confirm()
    await waitFor(() => expect(resetSetting).toHaveBeenCalledTimes(2))
    expect(resetSetting).toHaveBeenNthCalledWith(2, key, 'Điều chỉnh hạn mức.')
  })
  it('reloads after 404, retains draft for an existing key and never repeats PUT', async () => {
    vi.mocked(updateSetting).mockRejectedValue(failure(404, 'SETTING_NOT_FOUND'))
    await open()
    confirm()
    await screen.findByText(/Đã tải lại danh mục/)
    expect(screen.getByLabelText('Giá trị mới', { exact: false })).toHaveValue('30')
    expect(screen.getByLabelText('Lý do', { exact: false })).toHaveValue('Điều chỉnh hạn mức.')
    expect(updateSetting).toHaveBeenCalledOnce()
    expect(listSettings).toHaveBeenCalledTimes(3)
  })
  it('reconciles removed keys after 404 without keeping an unusable editor', async () => {
    await open()
    vi.mocked(listSettings).mockResolvedValue(settingsFixtures.filter((item) => item.key !== key))
    vi.mocked(updateSetting).mockRejectedValue(failure(404, 'SETTING_NOT_FOUND'))
    confirm()
    await waitFor(() =>
      expect(screen.queryByRole('article', { name: key })).not.toBeInTheDocument(),
    )
    expect(updateSetting).toHaveBeenCalledOnce()
  })
  it('requires a successful read after an ambiguous write; keeps draft if reconciliation fails', async () => {
    await open()
    vi.mocked(updateSetting).mockRejectedValue(failure(0, 'NETWORK_ERROR'))
    vi.mocked(listSettings).mockRejectedValue(failure(0, 'NETWORK_ERROR'))
    confirm()
    expect(await screen.findByText(/Chưa xác nhận được kết quả lưu/)).toBeVisible()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Tiếp tục' })).toBeDisabled())
    expect(screen.getByLabelText('Giá trị mới', { exact: false })).toHaveValue('30')
    vi.mocked(listSettings).mockResolvedValue(settingsFixtures)
    fireEvent.click(screen.getByRole('button', { name: 'Tải lại cài đặt' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Tiếp tục' })).toBeEnabled())
    expect(updateSetting).toHaveBeenCalledOnce()
  })
  it('keeps a newly selected editor when a late read removes the previous key', async () => {
    await open()
    vi.mocked(updateSetting).mockRejectedValue(failure(0, 'NETWORK_ERROR'))
    vi.mocked(listSettings).mockRejectedValueOnce(failure(0, 'NETWORK_ERROR'))
    confirm()
    await screen.findByText(/Chưa đọc lại được kết quả lần lưu trước/)
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Tải lại cài đặt' })).toBeEnabled(),
    )
    const read = deferred<SettingItem[]>()
    const remaining = settingsFixtures.filter((item) => item.key !== key)
    vi.mocked(listSettings).mockReturnValueOnce(read.promise).mockResolvedValue(remaining)
    fireEvent.click(screen.getByRole('button', { name: 'Tải lại cài đặt' }))
    fireEvent.click(screen.getByRole('button', { name: 'Sửa grading.min-hanzi' }))
    await act(async () => read.resolve(remaining))
    expect(await screen.findByRole('form', { name: 'Thay đổi grading.min-hanzi' })).toBeVisible()
    expect(screen.getByLabelText('Giá trị mới', { exact: false })).toHaveValue('100')
    expect(updateSetting).toHaveBeenCalledOnce()
  })
  it('ignores stale history after switching keys and requests page zero for the new key', async () => {
    const old = deferred<SettingHistoryPage>()
    vi.mocked(getSettingHistory).mockImplementation((requested) =>
      requested === key
        ? old.promise
        : Promise.resolve({
            ...historyFixture,
            items: [{ ...historyFixture.items[0]!, reason: 'Lịch sử khóa mới.' }],
          }),
    )
    render(<Scr078SystemSettingsPage />)
    fireEvent.click(await screen.findByRole('button', { name: `Lịch sử ${key}` }))
    await waitFor(() =>
      expect(getSettingHistory).toHaveBeenCalledWith(key, 0, expect.any(AbortSignal)),
    )
    fireEvent.click(screen.getByRole('button', { name: 'Lịch sử grading.min-hanzi' }))
    await screen.findByText('Lịch sử khóa mới.')
    await act(async () => old.resolve(historyFixture))
    expect(screen.queryByText(historyFixture.items[0]!.reason)).not.toBeInTheDocument()
    expect(getSettingHistory).toHaveBeenLastCalledWith(
      'grading.min-hanzi',
      0,
      expect.any(AbortSignal),
    )
  })
  it('loads history pages and refreshes open history after successful reset', async () => {
    vi.mocked(getSettingHistory).mockResolvedValue({ ...historyFixture, totalElements: 21 })
    await open('reset')
    fireEvent.click(screen.getByRole('button', { name: `Lịch sử ${key}` }))
    await screen.findByText(historyFixture.items[0]!.reason)
    fireEvent.click(screen.getByRole('button', { name: 'Sau' }))
    await waitFor(() =>
      expect(getSettingHistory).toHaveBeenCalledWith(key, 1, expect.any(AbortSignal)),
    )
    confirm()
    await screen.findByText('Đã đưa cài đặt về mặc định.')
    await waitFor(() => expect(getSettingHistory).toHaveBeenCalledTimes(3))
  })
  it.each([
    ['available', usageFixture, '123.456 / 1.000.000'],
    ['zero', { ...usageFixture, used: 0 }, '0 / 1.000.000'],
    ['unavailable', { ...usageFixture, used: null, available: false }, 'Chưa đọc được số liệu'],
  ] as const)(
    'shows token usage state %s independently of settings',
    async (_state, data, text) => {
      vi.mocked(getAiUsage).mockResolvedValue(data as AiUsage)
      render(<Scr078SystemSettingsPage />)
      expect(await screen.findByText(text)).toBeVisible()
      expect(await screen.findByRole('article', { name: key })).toBeVisible()
    },
  )
  it.each([404, 0])('shows no fabricated zero for missing/network usage (%s)', async (status) => {
    vi.mocked(getAiUsage).mockRejectedValue(failure(status))
    render(<Scr078SystemSettingsPage />)
    expect(await screen.findByText('Chưa có dữ liệu')).toBeVisible()
    expect(
      within(screen.getByRole('contentinfo', { name: 'Số liệu token' })).queryByText(
        '0 / 1.000.000',
      ),
    ).not.toBeInTheDocument()
    expect(await screen.findByRole('article', { name: key })).toBeVisible()
  })
  it('refreshes usage after updating its budget', async () => {
    render(<Scr078SystemSettingsPage />)
    fireEvent.click(await screen.findByRole('button', { name: 'Sửa ai.monthly-token-budget' }))
    vi.mocked(updateSetting).mockResolvedValue({ ...settingsFixtures[1]!, currentValue: 2000000 })
    confirm('2000000')
    await screen.findByText('Đã lưu cài đặt.')
    expect(getAiUsage).toHaveBeenCalledTimes(2)
  })
  it('shows catalog error and empty states without blocking the usage footer', async () => {
    vi.mocked(listSettings).mockRejectedValue(failure(500))
    render(<Scr078SystemSettingsPage />)
    await screen.findByText('Không thể áp dụng giá trị.')
    expect(await screen.findByText('123.456 / 1.000.000')).toBeVisible()
    vi.mocked(listSettings).mockResolvedValue([])
    fireEvent.click(screen.getByRole('button', { name: 'Tải lại cài đặt' }))
    expect(await screen.findByText('Chưa có cài đặt')).toBeVisible()
  })
})
