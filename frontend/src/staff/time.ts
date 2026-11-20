/**
 * Order times from the backend are local times in Bremen without a zone
 * ("2026-11-11T14:32:05.123"); handling times are instants with "Z". These
 * helpers show both in Bremen time, whatever the device is set to.
 */

const CAFE_ZONE = 'Europe/Berlin'

/** "2026-11-11", today's date in Bremen. */
export function bremenToday(now = new Date()) {
  return bremenNow(now).slice(0, 10)
}

/** "14:32" for today, "31.10., 17:22" for earlier days. */
export function clockTime(localBremenTime: string, now = new Date()) {
  const time = localBremenTime.slice(11, 16)
  if (localBremenTime.slice(0, 10) === bremenToday(now)) return time

  const [, month, day] = localBremenTime.slice(0, 10).split('-')
  return `${day}.${month}., ${time}`
}

/** "14:32" from an instant. */
export function bremenClockTime(instant: string) {
  return new Intl.DateTimeFormat('de-DE', {
    hour: '2-digit',
    minute: '2-digit',
    timeZone: CAFE_ZONE,
  }).format(new Date(instant))
}

/** The current time in Bremen as "2026-11-11T14:32:05". */
function bremenNow(now: Date) {
  // Swedish formatting happens to be ISO-like: "2026-11-11 14:32:05".
  return new Intl.DateTimeFormat('sv-SE', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    timeZone: CAFE_ZONE,
  })
    .format(now)
    .replace(' ', 'T')
}

/** "gerade eben", "vor 5 Min.", "vor 1 Std. 10 Min." */
export function waitingFor(localBremenTime: string, now = new Date()) {
  const toMs = (local: string) => Date.parse(`${local.slice(0, 19)}Z`)
  const minutes = Math.max(0, Math.floor((toMs(bremenNow(now)) - toMs(localBremenTime)) / 60_000))

  if (minutes < 1) return 'gerade eben'
  if (minutes < 60) return `vor ${minutes} Min.`
  if (minutes < 24 * 60) return `vor ${Math.floor(minutes / 60)} Std. ${minutes % 60} Min.`
  const days = Math.floor(minutes / (24 * 60))
  return days === 1 ? 'seit gestern' : `seit ${days} Tagen`
}

/** Minutes waited, for highlighting orders that wait long. */
export function minutesWaiting(localBremenTime: string, now = new Date()) {
  const toMs = (local: string) => Date.parse(`${local.slice(0, 19)}Z`)
  return Math.floor((toMs(bremenNow(now)) - toMs(localBremenTime)) / 60_000)
}
