import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { useApiQuery } from '@/shared/api'

import { getKnowledgeTree } from '../api/knowledge'
import { listQuestions } from '../api/questions'
import { QuestionBankListView } from '../components/QuestionBankListView'
import {
  parseQuestionListSearchParams,
  QUESTION_PAGE_SIZE,
  questionListSearchParams,
  questionSearchError,
  type QuestionListQuery,
} from '../questionListQuery'

export default function Scr064QuestionBankPage() {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const parsed = useMemo(() => parseQuestionListSearchParams(searchParams), [searchParams])
  const { query } = parsed
  const [searchDraft, setSearchDraft] = useState(query.q)
  const [searchError, setSearchError] = useState<string>()

  useEffect(() => {
    if (parsed.needsCleanup) setSearchParams(parsed.canonical, { replace: true })
  }, [parsed.canonical, parsed.needsCleanup, setSearchParams])

  useEffect(() => {
    setSearchDraft(query.q)
    setSearchError(undefined)
  }, [query.q])

  const questions = useApiQuery(
    (signal) =>
      listQuestions(
        {
          hskLevel: query.hskLevel || undefined,
          qtype: query.qtype || undefined,
          status: query.status || undefined,
          kid: query.kid || undefined,
          q: query.q || undefined,
          page: query.page,
          size: QUESTION_PAGE_SIZE,
        },
        signal,
      ),
    [query.hskLevel, query.qtype, query.status, query.kid, query.q, query.page],
    { isEmpty: (data) => data.items.length === 0 },
  )
  const tree = useApiQuery((signal) => getKnowledgeTree({}, signal), [])
  const updateQuery = (next: QuestionListQuery) => {
    setSearchParams(questionListSearchParams(next, searchParams))
  }

  return (
    <QuestionBankListView
      data={questions.data}
      error={questions.error}
      nodes={tree.data?.nodes ?? []}
      nodesError={tree.error}
      nodesState={tree.state}
      onClearFilters={() => {
        setSearchDraft('')
        setSearchError(undefined)
        updateQuery({ hskLevel: '', qtype: '', status: '', kid: '', q: '', page: 0 })
      }}
      onFiltersChange={(next) => updateQuery({ ...next, page: 0 })}
      onNodesRetry={tree.reload}
      onOpenQuestion={(id) => navigate(`/content/questions/${id}?${searchParams.toString()}`)}
      onPageChange={(page) => updateQuery({ ...query, page })}
      onRetry={questions.reload}
      onSearchChange={(value) => {
        setSearchDraft(value)
        setSearchError(undefined)
      }}
      onSearchSubmit={() => {
        const error = questionSearchError(searchDraft)
        setSearchError(error)
        if (error) return
        const next = questionListSearchParams(
          { ...query, q: searchDraft.trim(), page: 0 },
          searchParams,
        )
        if (next.toString() === searchParams.toString()) questions.reload()
        else setSearchParams(next)
      }}
      query={query}
      searchDraft={searchDraft}
      searchError={searchError}
      state={questions.state}
    />
  )
}
