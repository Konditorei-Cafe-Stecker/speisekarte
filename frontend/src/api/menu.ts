export type MenuItem = {
  id: number
  name: string
  description: string | null
  price: number
  category: string
  available: boolean
  subcategory: string | null
  sizeVolume: string | null
  notes: string | null
}

export async function fetchMenuItems(
  signal?: AbortSignal,
): Promise<MenuItem[]> {
  const response = await fetch('/api/menu', { signal })

  if (!response.ok) {
    throw new Error(`Menu request failed: ${response.status}`)
  }

  const data: unknown = await response.json()

  if (!Array.isArray(data)) {
    throw new Error('Menu response must be a list')
  }

  return data as MenuItem[]
}