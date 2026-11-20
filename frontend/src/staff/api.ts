/**
 * Requests from the staff area to the backend.
 *
 * Staff stay signed in with a session cookie. Every changing request also
 * sends the CSRF token: the backend sets it as the XSRF-TOKEN cookie, and we
 * send that value back in the X-XSRF-TOKEN header.
 */

import type { OrderStatus } from '../api/orders'

export type StaffRole = 'STAFF' | 'ADMIN'

/** One order in the waiter's lists. createdAt is local time in Bremen. */
export type StaffOrderSummary = {
  orderReference: string
  shortCode: string | null
  createdAt: string
  status: OrderStatus
  totalPrice: number
  itemCount: number
  hasNote: boolean
  handledAt: string | null
  handledBy: string | null
}

export type StaffOrderLine = {
  menuItemId: number
  productName: string
  unitPrice: number
  /** Confirmed quantity; 0 when removed. */
  quantity: number
  /** What the guest ordered; 0 for lines the waiter added. */
  originalQuantity: number
  lineTotal: number
}

export type StaffOrderDetail = {
  orderReference: string
  shortCode: string | null
  createdAt: string
  status: OrderStatus
  note: string | null
  totalPrice: number
  handledAt: string | null
  handledBy: string | null
  items: StaffOrderLine[]
}

export type CurrentStaff = {
  username: string
  displayName: string
  role: StaffRole
  mustChangePassword: boolean
}

export type StaffAccount = {
  id: number
  username: string
  displayName: string
  role: StaffRole
  active: boolean
  mustChangePassword: boolean
  createdAt: string
  lastLoginAt: string | null
}

/** A failed request, with the German text from the backend. */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

const GENERIC_ERROR = 'Es ist ein Fehler aufgetreten. Bitte versuch es noch einmal.'
const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

let onSessionEnded: () => void = () => {}

/** Called whenever the backend answers 401: the session is over. */
export function setSessionEndedHandler(handler: () => void) {
  onSessionEnded = handler
}

function readCookie(name: string) {
  const entry = document.cookie
    .split('; ')
    .find((part) => part.startsWith(`${name}=`))

  return entry ? decodeURIComponent(entry.slice(name.length + 1)) : null
}

async function csrfToken() {
  const existing = readCookie(CSRF_COOKIE)
  if (existing) return existing

  const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' })
  const token = readCookie(CSRF_COOKIE)

  if (!response.ok || !token) {
    throw new ApiError(response.status, GENERIC_ERROR)
  }

  return token
}

async function errorFrom(response: Response) {
  let message = GENERIC_ERROR

  try {
    const body: unknown = await response.json()
    if (
      body &&
      typeof body === 'object' &&
      'detail' in body &&
      typeof body.detail === 'string'
    ) {
      message = body.detail
    }
  } catch {
    // No JSON body: keep the general message.
  }

  return new ApiError(response.status, message)
}

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PATCH'
  json?: unknown
  form?: Record<string, string>
  /** Sign-in itself must not count as "session ended" on 401. */
  quiet401?: boolean
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET'

  async function send() {
    // The header is read from the same cookie the backend compares it with,
    // so it always matches while the cookie exists.
    const headers: Record<string, string> = { Accept: 'application/json' }
    let body: BodyInit | undefined

    if (method !== 'GET') {
      headers[CSRF_HEADER] = await csrfToken()
    }
    if (options.json !== undefined) {
      headers['Content-Type'] = 'application/json'
      body = JSON.stringify(options.json)
    }
    if (options.form) {
      body = new URLSearchParams(options.form)
    }

    return fetch(path, { method, headers, body, credentials: 'same-origin' })
  }

  const response = await send()

  if (response.status === 401 && !options.quiet401) {
    onSessionEnded()
  }

  if (!response.ok) {
    throw await errorFrom(response)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

export const staffApi = {
  me: () => request<CurrentStaff>('/api/auth/me', { quiet401: true }),

  signIn: (username: string, password: string) =>
    request<CurrentStaff>('/api/auth/login', {
      method: 'POST',
      form: { username, password },
      quiet401: true,
    }),

  signOut: () => request<void>('/api/auth/logout', { method: 'POST', quiet401: true }),

  changePassword: (currentPassword: string, newPassword: string) =>
    request<CurrentStaff>('/api/auth/password', {
      method: 'POST',
      json: { currentPassword, newPassword },
    }),

  listOrders: (view: 'open' | 'today') =>
    request<StaffOrderSummary[]>(`/api/staff/orders?view=${view}`),

  getOrder: (reference: string) =>
    request<StaffOrderDetail>(`/api/staff/orders/${encodeURIComponent(reference)}`),

  findOrderByCode: (code: string) =>
    request<StaffOrderDetail>(`/api/staff/orders/code/${encodeURIComponent(code)}`),

  /** Without items: confirm as it is. With items: that is the final order. */
  confirmOrder: (reference: string, items?: { menuItemId: number; quantity: number }[]) =>
    request<StaffOrderDetail>(`/api/staff/orders/${encodeURIComponent(reference)}/confirm`, {
      method: 'POST',
      json: items ? { items } : {},
    }),

  cancelOrder: (reference: string) =>
    request<StaffOrderDetail>(`/api/staff/orders/${encodeURIComponent(reference)}/cancel`, {
      method: 'POST',
    }),

  listStaff: () => request<StaffAccount[]>('/api/admin/staff'),

  createStaff: (account: {
    username: string
    displayName: string
    role: StaffRole
    temporaryPassword: string
  }) => request<StaffAccount>('/api/admin/staff', { method: 'POST', json: account }),

  updateStaff: (
    id: number,
    changes: Partial<Pick<StaffAccount, 'displayName' | 'role' | 'active'>>,
  ) => request<StaffAccount>(`/api/admin/staff/${id}`, { method: 'PATCH', json: changes }),

  resetPassword: (id: number, temporaryPassword: string) =>
    request<void>(`/api/admin/staff/${id}/password`, {
      method: 'POST',
      json: { temporaryPassword },
    }),
}

export const MIN_PASSWORD_LENGTH = 10

/**
 * A random temporary password, easy to read aloud: no 0/O, 1/l/I.
 * 14 characters from 54 symbols, about 80 bits.
 */
export function suggestPassword() {
  const alphabet = 'abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  const values = crypto.getRandomValues(new Uint32Array(14))

  return Array.from(values, (value) => alphabet[value % alphabet.length])
    .join('')
    .replace(/(.{4})(?=.)/g, '$1-')
}
