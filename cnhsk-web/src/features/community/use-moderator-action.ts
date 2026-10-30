import { createContext, useContext } from 'react'

export type ModeratorActionLane = {
  busy: boolean
  run: <T>(operation: () => Promise<T>, onSuccess: (result: T) => void, onError: (error: unknown) => void) => void
}
export const ModeratorActionContext = createContext<ModeratorActionLane | null>(null)

export function useModeratorAction(): ModeratorActionLane {
  const context = useContext(ModeratorActionContext)
  if (!context) throw new Error('Thao tác kiểm duyệt cần ModeratorSession.')
  return context
}
