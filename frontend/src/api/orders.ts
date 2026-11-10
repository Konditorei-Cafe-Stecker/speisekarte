export type CreateOrderRequest = {
  items: {
    menuItemId: number
    quantity: number
  }[]
}

export type OrderResponse = {
  orderReference: string
  createdAt: string
  status: 'PENDING' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED'
  totalPrice: number
  items: {
    menuItemId: number
    productName: string
    unitPrice: number
    quantity: number
    lineTotal: number
  }[]
}

export async function createOrder(
  order: CreateOrderRequest,
): Promise<OrderResponse> {
  const response = await fetch('/api/orders', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(order),
  })

  if (!response.ok) {
    const message = await response.text()
    throw new Error(message || `Order failed: ${response.status}`)
  }

  return response.json() as Promise<OrderResponse>
}