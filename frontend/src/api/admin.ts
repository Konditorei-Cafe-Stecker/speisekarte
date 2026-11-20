export type ForecastHour = {
  hour: number
  expectedOrders: number
  ordersHigh: number
  staff: number
}

export type ForecastDay = {
  day: string
  expectedOrders: number
  ordersLow: number
  ordersHigh: number
  expectedRevenue: number
  revenueLow: number
  revenueHigh: number
  tempMin: number | null
  tempMax: number | null
  rainyHours: number
  events: string[]
  offers: string[]
  staffHours: number
  peakStaff: number
  hours: ForecastHour[]
}

export type ProductNeed = {
  menuItemId: number
  product: string
  category: string
  subcategory: string | null
  expectedUnits: number
  busyCaseUnits: number
  expectedRevenue: number
}

export type AdminForecast = {
  generatedAt: string
  basis: 'SYNTHETIC' | 'REAL' | 'ALL'
  from: string
  to: string
  staffing: { ordersPerStaffHour: number; minStaff: number }
  summary: {
    expectedOrders: number
    expectedRevenue: number
    revenueLow: number
    revenueHigh: number
    staffHours: number
    busiestDay: string
  }
  days: ForecastDay[]
  products: ProductNeed[]
}

export type StaffingInput = { ordersPerStaffHour: number; minStaff: number }

/** Error with a message that can be shown to the owner as it is. */
export class AdminError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export async function fetchAdminForecast(
  token: string,
  staffing: StaffingInput,
  signal?: AbortSignal,
): Promise<AdminForecast> {
  const params = new URLSearchParams({
    days: '7',
    ordersPerStaffHour: String(staffing.ordersPerStaffHour),
    minStaff: String(staffing.minStaff),
  })

  const response = await fetch(`/api/admin/forecast?${params}`, {
    headers: { 'X-Admin-Token': token },
    signal,
  })

  if (response.status === 401) {
    throw new AdminError(401, 'Der Zugangscode ist falsch.')
  }
  if (response.status === 503) {
    throw new AdminError(
      503,
      'Der Admin-Zugang ist auf dem Server noch nicht eingerichtet (STECKER_ADMIN_TOKEN).',
    )
  }
  if (response.status === 404) {
    throw new AdminError(
      404,
      'Für diese Woche wurde noch keine Prognose erstellt. Bitte die Prognose neu berechnen lassen.',
    )
  }
  if (!response.ok) {
    throw new AdminError(response.status, `Die Prognose konnte nicht geladen werden (${response.status}).`)
  }

  return (await response.json()) as AdminForecast
}
