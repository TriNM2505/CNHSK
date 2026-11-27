import { matchRoutes } from 'react-router-dom'

import { appRouteDefinitions } from '@/app/routeAccess'
import { screenRegistry, type ScreenId } from '@/app/screenRegistry'

const routes = appRouteDefinitions.map((route) => ({ path: route.path, route }))

/** The registered screen the learner is on; the assistant stays available on every screen, tests included. */
export function assistantPageContext(location: { pathname: string }) {
  const matches = matchRoutes(routes, location)
  const route = matches?.[matches.length - 1]?.route.route
  const pageContext: ScreenId | undefined =
    route?.kind === 'screen' && Object.prototype.hasOwnProperty.call(screenRegistry, route.scr)
      ? route.scr
      : undefined
  return { pageContext }
}
