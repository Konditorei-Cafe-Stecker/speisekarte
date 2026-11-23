import type { MenuItem } from './menu'

export type RecommendationKind =
  | 'WARM_DRINKS'
  | 'CHRISTMAS_MARKET'
  | 'BREAKFAST'
  | 'MATCH_DAY'
  | 'ICE_CREAM'

/** One recommendation for today, with the dishes it refers to. */
export type Recommendation = {
  kind: RecommendationKind
  title: string
  text: string
  /** Local time in Bremen it starts ("09:00:00"), or null for all day. */
  validFrom: string | null
  /** Local time in Bremen it ends ("12:00:00"), or null for all day. */
  validUntil: string | null
  /** Normal menu items with their normal prices; available ones only. */
  products: MenuItem[]
}

export type TodayRecommendations = {
  /** "2026-11-23", today in Bremen. */
  day: string
  recommendations: Recommendation[]
}

/** Today's recommendations; an empty list when there are none. */
export async function fetchTodayRecommendations(
  signal?: AbortSignal,
): Promise<TodayRecommendations> {
  const response = await fetch('/api/recommendations/today', { signal })

  if (!response.ok) {
    throw new Error(`Recommendations request failed: ${response.status}`)
  }

  return response.json() as Promise<TodayRecommendations>
}

/** "Montag, 23. November" for a day like "2026-11-23". */
export function formatRecommendationDay(day: string) {
  return new Intl.DateTimeFormat('de-DE', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    timeZone: 'UTC',
  }).format(new Date(`${day}T00:00:00Z`))
}

/**
 * Date and time in Bremen as "2026-11-23 14:05", whatever the device is set
 * to. (Swedish formatting happens to match the API's ISO dates.)
 */
export function bremenNow(now = new Date()) {
  return new Intl.DateTimeFormat('sv-SE', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
    timeZone: 'Europe/Berlin',
  }).format(now)
}

/**
 * The recommendations still on at `now` (from bremenNow). A page left
 * open drops breakfast at 12:00 without a reload, and shows nothing once
 * the day it was loaded for is over.
 */
export function currentRecommendations(today: TodayRecommendations | null, now: string) {
  const [day, time] = now.split(' ')
  if (!today || today.day !== day) return []
  return today.recommendations.filter(
    (pick) => pick.validUntil === null || time < pick.validUntil.slice(0, 5),
  )
}

/** "9 Uhr", "17:30 Uhr" style for "09:00:00". */
function clock(time: string) {
  const [hours, minutes] = time.split(':')
  return minutes === '00' ? `${Number(hours)}` : `${Number(hours)}:${minutes}`
}

/** "9–12 Uhr", "bis 17 Uhr", or null for all day. */
export function formatWindow(recommendation: Recommendation) {
  const { validFrom, validUntil } = recommendation
  if (validFrom && validUntil) return `${clock(validFrom)}–${clock(validUntil)} Uhr`
  if (validUntil) return `bis ${clock(validUntil)} Uhr`
  return null
}
