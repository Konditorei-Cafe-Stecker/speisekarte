const euroFormatter = new Intl.NumberFormat('de-DE', {
  style: 'currency',
  currency: 'EUR',
})

/** Prices are added up in integer cents to avoid floating-point errors. */
export function toCents(price: number) {
  return Math.round(price * 100)
}

export function formatEuro(amount: number) {
  return euroFormatter.format(amount)
}

export function formatCents(cents: number) {
  return euroFormatter.format(cents / 100)
}
