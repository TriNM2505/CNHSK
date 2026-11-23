import { createContext } from 'react'

/**
 * True only inside AuthProvider, i.e. a real browser session. Previews and tests that supply AuthContext with sample
 * users stay offline, so background widgets such as the notification bell must not call the API there.
 */
export const LiveSessionContext = createContext(false)