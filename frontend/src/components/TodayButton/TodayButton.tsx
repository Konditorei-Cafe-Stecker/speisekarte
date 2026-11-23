import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import './TodayButton.css'
import ArrowIcon from '../ArrowIcon/ArrowIcon'
import {
  currentRecommendations,
  fetchTodayRecommendations,
} from '../../api/recommendations'
import type { TodayRecommendations } from '../../api/recommendations'
import { useBremenNow } from '../Recommendations/useBremenNow'
import { ScrollTrigger } from '../../utils/motion'

/**
 * Startseite: one button to today's recommendations at the top of the
 * Speisekarte. Shows nothing while there are none (no forecast for today,
 * or breakfast was the only one and it is past 12:00).
 */
function TodayButton() {
  const [today, setToday] = useState<TodayRecommendations | null>(null)

  useEffect(() => {
    const controller = new AbortController()

    fetchTodayRecommendations(controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setToday(result)
      })
      .catch(() => {})

    return () => controller.abort()
  }, [])

  const now = useBremenNow()
  const visible = currentRecommendations(today, now).length > 0

  // The strip appears (or goes) after the page has loaded and moves the
  // pinned Spezialitäten gallery below it: measure the triggers again.
  const wasVisible = useRef(false)
  useEffect(() => {
    if (wasVisible.current === visible) return
    wasVisible.current = visible
    ScrollTrigger.refresh()
  }, [visible])

  if (!visible) return null

  return (
    <div className="today-button-band">
      <div className="container">
        <Link to="/menu" className="btn btn--accent today-button">
          <span className="today-button-dot" aria-hidden="true" />
          Heute empfohlen
          <span className="visually-hidden"> – auf der Speisekarte</span>
          <ArrowIcon />
        </Link>
      </div>
    </div>
  )
}

export default TodayButton
