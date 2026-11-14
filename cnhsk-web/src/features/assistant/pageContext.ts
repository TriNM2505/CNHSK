import { matchPath, matchRoutes } from 'react-router-dom'

import { appRouteDefinitions } from '@/app/routeAccess'
import { screenRegistry, type ScreenId } from '@/app/screenRegistry'

const routes = appRouteDefinitions.map((route) => ({ path: route.path, route }))

export function assistantPageContext(location: { pathname: string }) {
  const matches = matchRoutes(routes, location)
  const route = matches?.[matches.length - 1]?.route.route
  const pageContext: ScreenId | undefined =
    route?.kind === 'screen' && Object.prototype.hasOwnProperty.call(screenRegistry, route.scr)
      ? route.scr
      : undefined
  const hidden =
    pageContext === 'SCR-021' ||
    pageContext === 'SCR-044' ||
    Boolean(matchPath({ path: '/learn/topics/:id/test', end: true }, location.pathname))
  return { pageContext, hidden }
}
