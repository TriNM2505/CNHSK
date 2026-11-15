import { useEffect, useRef, useState } from 'react'

import { toApiError, useApiQuery } from '@/shared/api'

import {
  getAiUsage,
  getSettingHistory,
  listSettings,
  resetSetting,
  updateSetting,
} from '../api/settings'
import { SettingsView } from '../components/SettingsView'
import { validateSetting } from '../settingsForm'
import type { SettingDraft, SettingItem } from '../types'

export default function Scr078SystemSettingsPage() {
  const catalog = useApiQuery(listSettings, [], { isEmpty: (items) => !items.length })
  const usage = useApiQuery(getAiUsage, [])
  const [draft, setDraft] = useState<SettingDraft>()
  const [changedItem, setChangedItem] = useState<SettingItem>()
  const [historyKey, setHistoryKey] = useState<string>()
  const [historyPage, setHistoryPage] = useState(0)
  const [pending, setPending] = useState(false)
  const [blocked, setBlocked] = useState(false)
  const [message, setMessage] = useState<string>()
  const [error, setError] = useState<{ message: string; requestId?: string }>()
  const busy = useRef(false)
  const mounted = useRef(false)
  const reconciliation = useRef<AbortController>()
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
      reconciliation.current?.abort()
    }
  }, [])
  useEffect(() => {
    setChangedItem(undefined)
  }, [catalog.data])
  const items = (catalog.data ?? []).map((item) =>
    item.key === changedItem?.key ? changedItem : item,
  )
  const item = items.find((row) => row.key === draft?.key)
  const history = useApiQuery(
    async (signal) =>
      historyKey
        ? {
            key: historyKey,
            page: historyPage,
            data: await getSettingHistory(historyKey, historyPage, signal),
          }
        : undefined,
    [historyKey, historyPage],
  )

  function edit(selected: SettingItem, mode: SettingDraft['mode']) {
    if (busy.current) return
    setDraft({
      key: selected.key,
      mode,
      value: String(selected.currentValue),
      reason: '',
      confirming: false,
    })
    setError(undefined)
    setMessage(undefined)
    // An uncertain write must be reconciled before another mutation.
  }

  async function readAfterUncertainWrite(key: string) {
    reconciliation.current?.abort()
    const controller = new AbortController()
    reconciliation.current = controller
    try {
      const rows = await listSettings(controller.signal)
      if (!mounted.current || controller.signal.aborted) return
      setChangedItem(rows.find((row) => row.key === key))
      if (!rows.some((row) => row.key === key))
        setDraft((current) => (current?.key === key ? undefined : current))
      setMessage(
        'Đã tải lại danh mục. Hãy kiểm tra giá trị hiện tại trước khi thực hiện thay đổi tiếp theo.',
      )
      setBlocked(false)
      catalog.reload()
    } catch {
      // Keep the draft and block writes until an explicit read succeeds.
    }
  }

  async function submit() {
    if (!draft || !item || busy.current || blocked || !draft.confirming) return
    const validation = validateSetting(item, draft, items)
    if (validation.value || validation.reason) return
    busy.current = true
    setPending(true)
    setError(undefined)
    try {
      const updated =
        draft.mode === 'reset'
          ? await resetSetting(item.key, draft.reason)
          : await updateSetting(item.key, Number(draft.value), draft.reason)
      if (!mounted.current) return
      setChangedItem(updated)
      setDraft(undefined)
      setMessage(draft.mode === 'reset' ? 'Đã đưa cài đặt về mặc định.' : 'Đã lưu cài đặt.')
      catalog.reload()
      history.reload()
      if (item.key === 'ai.monthly-token-budget') usage.reload()
    } catch (caught) {
      if (!mounted.current) return
      const failure = toApiError(caught)
      setDraft((current) => (current ? { ...current, confirming: false } : current))
      setError({ message: failure.message, requestId: failure.requestId })
      if (failure.status === 404 || failure.status === 0 || failure.status >= 500) {
        setBlocked(true)
        setError({
          message:
            failure.status === 404
              ? 'Danh mục đã thay đổi. Hãy kiểm tra dữ liệu tải lại trước khi xác nhận.'
              : 'Chưa xác nhận được kết quả lưu. Hãy kiểm tra dữ liệu tải lại trước khi gửi lại.',
          requestId: failure.requestId,
        })
        await readAfterUncertainWrite(item.key)
      }
    } finally {
      busy.current = false
      if (mounted.current) setPending(false)
    }
  }

  return (
    <SettingsView
      state={catalog.state}
      items={items}
      error={catalog.error}
      pending={pending}
      onRetry={() => {
        if (blocked) void readAfterUncertainWrite(draft?.key ?? '')
        else catalog.reload()
      }}
      onEdit={edit}
      onHistory={(key) => {
        if (!busy.current) {
          setHistoryKey(key)
          setHistoryPage(0)
        }
      }}
      message={
        blocked
          ? 'Chưa đọc lại được kết quả lần lưu trước. Chọn Tải lại cài đặt trước khi tiếp tục thay đổi.'
          : message
      }
      usage={{ ...usage, onRetry: usage.reload }}
      editor={
        item && draft
          ? {
              item,
              catalog: items,
              draft,
              pending,
              blocked,
              error,
              onChange: (next) => {
                if (!busy.current) {
                  setDraft(next)
                  setError(undefined)
                }
              },
              onSubmit: () => void submit(),
              onCancel: () => {
                if (!busy.current) {
                  setDraft(undefined)
                  setError(undefined)
                }
              },
            }
          : undefined
      }
      history={
        historyKey
          ? {
              settingKey: historyKey,
              state:
                history.state === 'ready' &&
                (history.data?.key !== historyKey || history.data.page !== historyPage)
                  ? 'loading'
                  : history.state,
              data:
                history.data?.key === historyKey && history.data.page === historyPage
                  ? history.data.data
                  : undefined,
              error: history.error,
              pending,
              onPage: setHistoryPage,
              onRetry: history.reload,
              onClose: () => {
                if (!busy.current) setHistoryKey(undefined)
              },
            }
          : undefined
      }
    />
  )
}
