/**
 * Facts about the café that appear in several places. Change them here
 * and they update on the Startseite and on "Über uns".
 */

// Opening hours as listed for the café on Google Maps (November 2026).
// Public holidays are not covered.
const mondayToSaturday = '9–18 Uhr'
const sunday = '13–18 Uhr'

const address = 'Knochenhauerstraße 14, 28195 Bremen'

export const site = {
  /** Today's hours, by the weekday in Bremen (not the visitor's own). */
  get hoursToday() {
    const weekday = new Intl.DateTimeFormat('en-US', {
      weekday: 'short',
      timeZone: 'Europe/Berlin',
    }).format(new Date())

    return weekday === 'Sun' ? sunday : mondayToSaturday
  },
  hours: [
    { days: 'Montag–Samstag', time: mondayToSaturday },
    { days: 'Sonntag', time: sunday },
  ],
  name: 'Konditorei Café Stecker',
  street: 'Knochenhauerstraße 14',
  postcode: '28195',
  city: 'Bremen',
  phone: '0421 12593',
  /** The same number for a tap-to-call link. */
  phoneHref: 'tel:+4942112593',
  /** Opens directions to the café in Google Maps. */
  routeUrl: `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(address)}`,
  /** Map shown on "Über uns" after the guest agrees to load it. */
  mapEmbedUrl: `https://www.google.com/maps?q=${encodeURIComponent(address)}&output=embed`,
}

/**
 * The four rows in "Aus der Karte" on the Startseite. Prices always come
 * from /api/menu; only the wording can be overridden here.
 */
export const menuTeaser: { id: number; title?: string; detail?: string }[] = [
  { id: 92, title: 'Schneemustorte Erdbeere' },
  { id: 10 },
  { id: 76 },
  { id: 98, detail: 'Konditor-Eis aus eigener Herstellung' },
]
