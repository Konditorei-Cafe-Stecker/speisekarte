/**
 * Small sessionStorage helpers. Storage can be unavailable (private mode,
 * blocked site data), so every access is wrapped and failures are ignored.
 */
export function readSession<T>(
  key: string,
  parse: (value: unknown) => T | null,
): T | null {
  try {
    const raw = window.sessionStorage.getItem(key)
    if (raw === null) return null

    return parse(JSON.parse(raw))
  } catch {
    return null
  }
}

export function writeSession(key: string, value: unknown) {
  try {
    window.sessionStorage.setItem(key, JSON.stringify(value))
  } catch {
    // The page works without persistence.
  }
}

export function removeSession(key: string) {
  try {
    window.sessionStorage.removeItem(key)
  } catch {
    // Nothing to clean up.
  }
}
