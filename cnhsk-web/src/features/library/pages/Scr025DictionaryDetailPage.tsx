import { useLocation, useParams, useSearchParams } from 'react-router-dom'

import { ApiError, useApiQuery } from '@/shared/api'
import { useCurrentUser } from '@/shared/auth'

import { getLexeme } from '../api/dictionary'
import { addCard, listDecks } from '../api/flashcards'
import { createNote } from '../api/notes'
import { LexemeDetailView } from '../components/LexemeDetailView'
import { LexemeSaveActions, type LexemeSaveActionsProps } from '../components/LexemeSaveActions'
import { dictionaryReturnTo } from '../dictionarySearch'

function DictionaryDetailContent({ rawId }: { rawId: string }) {
  const auth = useCurrentUser()
  const location = useLocation()
  const [params] = useSearchParams()
  const id = /^\d+$/.test(rawId) ? Number(rawId) : 0
  const query = useApiQuery(
    (signal) => {
      if (!Number.isSafeInteger(id) || id < 1)
        return Promise.reject(
          new ApiError({
            status: 404,
            errorCode: 'LEXEME_NOT_FOUND',
            message: 'Mục từ không tồn tại.',
          }),
        )
      return getLexeme(id, signal)
    },
    [id],
  )
  let access: LexemeSaveActionsProps['access'] =
    auth.status === 'authenticated' ? 'other' : auth.status
  if (auth.status === 'authenticated' && auth.user?.roles.includes('LEARNER')) access = 'learner'

  return (
    <LexemeDetailView
      entry={query.data}
      state={query.state}
      error={query.error}
      backTo={dictionaryReturnTo(params.get('from'))}
      onRetry={query.reload}
      saveActions={
        query.data ? (
          <LexemeSaveActions
            key={`${id}:${auth.user?.id ?? ''}:${access}`}
            entry={query.data}
            access={access}
            loginTo={`/login?${new URLSearchParams({ redirect: `${location.pathname}${location.search}${location.hash}` })}`}
            onReloadAuth={() => {
              void auth.reloadCurrentUser()
            }}
            onCreateNote={createNote}
            onLoadDecks={listDecks}
            onAddCard={addCard}
          />
        ) : undefined
      }
    />
  )
}

export default function Scr025DictionaryDetailPage() {
  const { id = '' } = useParams()
  return <DictionaryDetailContent key={id} rawId={id} />
}
