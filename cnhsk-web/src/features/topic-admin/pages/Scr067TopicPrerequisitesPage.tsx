import { useEffect, useRef, useState } from 'react'

import { toApiError, useApiQuery } from '@/shared/api'

import { listAdminTopics, readAdminTopicCatalog, setTopicPrerequisites } from '../api/topics'
import { TopicPrerequisitesView } from '../components/TopicPrerequisitesView'
import type { AdminTopic, TopicFilters, TopicRef } from '../types'

function cyclePath(details: unknown): number[] {
  if (!details || typeof details !== 'object' || !('path' in details)) return []
  const path = details.path
  return Array.isArray(path) && path.every((id) => Number.isSafeInteger(id) && id > 0) ? path : []
}

export default function Scr067TopicPrerequisitesPage() {
  const [filters, setFilters] = useState<TopicFilters>({ q: '', page: 0 })
  const [candidateFilters, setCandidateFilters] = useState<TopicFilters>({ q: '', page: 0 })
  const [selected, setSelected] = useState<AdminTopic>()
  const [chosen, setChosen] = useState<TopicRef[]>([])
  const [saving, setSaving] = useState(false)
  const [needsReload, setNeedsReload] = useState(false)
  const [error, setError] = useState<{ message: string; requestId?: string }>()
  const [cycleNames, setCycleNames] = useState<string[]>([])
  const [warnings, setWarnings] = useState<string[]>([])
  const [message, setMessage] = useState<string>()
  const busy = useRef(false)
  const mounted = useRef(false)
  const requestController = useRef<AbortController>()
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
      requestController.current?.abort()
    }
  }, [])

  const topics = useApiQuery((signal) => listAdminTopics(filters, signal), [filters])
  const candidates = useApiQuery(
    (signal) =>
      selected
        ? listAdminTopics(candidateFilters, signal)
        : Promise.resolve({ items: [], page: 0, size: 20, totalElements: 0 }),
    [candidateFilters, selected?.id],
  )

  function clearFeedback() {
    setError(undefined)
    setCycleNames([])
    setWarnings([])
    setMessage(undefined)
  }

  function chooseTopic(topic: AdminTopic) {
    if (busy.current) return
    setSelected(topic)
    setChosen(topic.prereqs)
    setNeedsReload(false)
    setCandidateFilters({ q: '', page: 0 })
    clearFeedback()
  }

  async function save() {
    if (!selected || busy.current || chosen.length > 10 || needsReload) return
    busy.current = true
    setSaving(true)
    clearFeedback()
    const controller = new AbortController()
    requestController.current = controller
    try {
      const updated = await setTopicPrerequisites(
        selected.id,
        chosen.map(({ id }) => id),
      )
      if (!mounted.current) return
      setSelected(updated)
      setChosen(updated.prereqs)
      setWarnings(updated.warnings ?? [])
      setMessage('Đã lưu tiên quyết chủ đề.')
      topics.reload()
      candidates.reload()
    } catch (caught: unknown) {
      if (!mounted.current) return
      const failure = toApiError(caught)
      setError({ message: failure.message, requestId: failure.requestId })
      if (failure.status === 422 && failure.errorCode === 'PREREQUISITE_CYCLE') {
        const path = cyclePath(failure.details)
        const known = new Map<number, string>()
        for (const topic of [
          selected,
          ...(topics.data?.items ?? []),
          ...(candidates.data?.items ?? []),
        ]) {
          for (const prerequisite of topic.prereqs) known.set(prerequisite.id, prerequisite.name)
        }
        for (const topic of [
          selected,
          ...chosen,
          ...(topics.data?.items ?? []),
          ...(candidates.data?.items ?? []),
        ]) {
          known.set(topic.id, topic.name)
        }
        if (path.some((id) => !known.has(id))) {
          try {
            const catalog = await readAdminTopicCatalog(controller.signal)
            for (const topic of catalog) known.set(topic.id, topic.name)
          } catch {
            if (mounted.current)
              setError({
                message: 'Có vòng tiên quyết. Không tải được đủ tên chủ đề; hãy tải lại danh sách.',
                requestId: failure.requestId,
              })
          }
        }
        if (mounted.current)
          setCycleNames(path.map((id) => known.get(id) ?? `Chủ đề #${id} (chưa tra được tên)`))
      } else if (failure.status === 404 || failure.status === 0 || failure.status >= 500) {
        // Read after an uncertain write; never automatically retry PUT.
        setNeedsReload(true)
        topics.reload()
        candidates.reload()
        try {
          const catalog = await readAdminTopicCatalog(controller.signal)
          if (!mounted.current) return
          const current = catalog.find(({ id }) => id === selected.id)
          if (current) {
            setSelected(current)
            setChosen((draft) =>
              draft
                .filter(({ id }) => catalog.some((topic) => topic.id === id))
                .map((topic) => catalog.find(({ id }) => id === topic.id)!),
            )
            setNeedsReload(false)
          } else {
            setSelected(undefined)
            setChosen([])
          }
          setError({
            message:
              failure.status === 404
                ? 'Dữ liệu chủ đề đã thay đổi. Đã tải lại; hãy kiểm tra lựa chọn trước khi lưu.'
                : 'Chưa xác nhận được kết quả lưu. Đã đọc lại dữ liệu; hãy kiểm tra tiên quyết trước khi gửi lại.',
            requestId: failure.requestId,
          })
        } catch {
          if (mounted.current)
            setError({
              message:
                'Không thể tải lại dữ liệu. Chọn lại chủ đề sau khi tải lại danh sách trước khi lưu.',
              requestId: failure.requestId,
            })
        }
      } else if (failure.errorCode === 'PREREQUISITE_NO_ROOT') {
        setError({
          message: 'Không thể lưu vì hệ thống cần ít nhất một chủ đề gốc. Hãy bỏ bớt tiên quyết.',
          requestId: failure.requestId,
        })
      }
    } finally {
      busy.current = false
      if (mounted.current) setSaving(false)
    }
  }

  const selectedIds = chosen.map(({ id }) => id)
  return (
    <TopicPrerequisitesView
      topics={{
        ...topics,
        filters,
        onFilter: setFilters,
        onRetry: topics.reload,
        onSelect: chooseTopic,
        pending: saving,
        selected,
        selectedIds,
      }}
      candidates={{
        ...candidates,
        filters: candidateFilters,
        onFilter: setCandidateFilters,
        onRetry: candidates.reload,
        onSelect: (topic) => {
          if (busy.current || topic.id === selected?.id) return
          clearFeedback()
          setChosen((current) =>
            current.some(({ id }) => id === topic.id)
              ? current.filter(({ id }) => id !== topic.id)
              : current.length < 10
                ? [...current, topic]
                : current,
          )
        },
        pending: saving,
        selected,
        selectedIds,
      }}
      chosen={chosen}
      cycleNames={cycleNames}
      error={error}
      message={message}
      onRemove={(id) => {
        if (!busy.current) {
          clearFeedback()
          setChosen((current) => current.filter((topic) => topic.id !== id))
        }
      }}
      onSave={() => void save()}
      saveDisabled={needsReload}
      saving={saving}
      selected={selected}
      warnings={warnings}
    />
  )
}
