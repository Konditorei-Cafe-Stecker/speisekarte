export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED'

export type CreateOrderRequest = {
  items: {
    menuItemId: number
    quantity: number
  }[]
  /** Optional wishes for the waiter. */
  note?: string
}

export type OrderResponse = {
  orderReference: string
  /** Four characters to read out; null for orders from before codes existed. */
  shortCode: string | null
  createdAt: string
  status: OrderStatus
  note: string | null
  totalPrice: number
  items: {
    menuItemId: number
    productName: string
    unitPrice: number
    quantity: number
    lineTotal: number
  }[]
}

export const MAX_NOTE_LENGTH = 300

/**
 * A refused request. `detail` is the backend's German explanation, meant
 * for the guest (for example "Espresso ist derzeit nicht verfügbar.").
 */
export class OrderRequestError extends Error {
  readonly status: number
  readonly detail: string | null

  constructor(status: number, detail: string | null) {
    super(detail ?? `Order request failed: ${status}`)
    this.status = status
    this.detail = detail
  }
}

async function failure(response: Response) {
  let detail: string | null = null

  try {
    const body: unknown = await response.json()
    if (body && typeof body === 'object' && 'detail' in body && typeof body.detail === 'string') {
      detail = body.detail
    }
  } catch {
    // No problem-detail body.
  }

  return new OrderRequestError(response.status, detail)
}

export async function createOrder(order: CreateOrderRequest): Promise<OrderResponse> {
  const response = await fetch('/api/orders', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(order),
  })

  if (!response.ok) throw await failure(response)

  return response.json() as Promise<OrderResponse>
}

/** The current state of a sent order, for the guest's confirmation. */
export async function fetchOrder(
  reference: string,
  signal?: AbortSignal,
): Promise<OrderResponse> {
  const response = await fetch(`/api/orders/${encodeURIComponent(reference)}`, { signal })

  if (!response.ok) throw await failure(response)

  return response.json() as Promise<OrderResponse>
}

/** Where the waiter's phone goes when it scans the guest's QR code. */
export function staffOrderUrl(reference: string) {
  return `${window.location.origin}/personal/bestellung/${reference}`
}
