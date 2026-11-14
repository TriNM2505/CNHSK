import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useApiMutation, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'
import { createGradingRequest, getGradingPolicy } from '../api/requests'
import { GradingSubmissionView } from '../components/GradingSubmissionView'
import { validSubmission } from '../essayRules'

function Submission() {
  const navigate = useNavigate()
  const policy = useApiQuery((signal) => getGradingPolicy(signal), [])
  const mutation = useApiMutation(createGradingRequest)
  const [essay, setEssay] = useState('')
  const [prompt, setPrompt] = useState('')
  const [hsk, setHsk] = useState('')
  const [confirming, setConfirming] = useState(false)
  const alive = useRef(true),
    busy = useRef(false)
  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
    }
  }, [])

  function send() {
    if (busy.current || !confirming || !validSubmission(essay, prompt, policy.data)) return
    busy.current = true
    void mutation
      .run({ essay, prompt: prompt || null, hskLevel: hsk ? Number(hsk) : null })
      .then((row) => {
        if (alive.current) navigate(`/grading/${row.id}`)
      })
      .catch(() => {
        if (alive.current) setConfirming(false)
      })
      .finally(() => {
        busy.current = false
      })
  }

  return (
    <GradingSubmissionView
      state={policy.state}
      policy={policy.data}
      error={policy.error}
      submitError={mutation.error}
      essay={essay}
      prompt={prompt}
      hsk={hsk}
      confirming={confirming}
      pending={mutation.pending}
      onEssay={(value) => {
        setEssay(value)
        mutation.reset()
      }}
      onPrompt={(value) => {
        setPrompt(value)
        mutation.reset()
      }}
      onHsk={(value) => {
        setHsk(value)
        mutation.reset()
      }}
      onRetry={policy.reload}
      onReview={() => {
        mutation.reset()
        setConfirming(true)
      }}
      onCancel={() => setConfirming(false)}
      onSend={send}
    />
  )
}

export default function Scr032GradingRequestSubmissionPage() {
  const auth = useCurrentUser()
  return auth.status === 'authenticated' && auth.user?.roles.includes('LEARNER') ? (
    <Submission key={auth.user.id} />
  ) : (
    <p role="status">Đang kiểm tra quyền truy cập…</p>
  )
}
