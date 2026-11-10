import { useRef } from 'react'
import { Link } from 'react-router-dom'
import './Story.css'
import ArrowIcon from '../ArrowIcon/ArrowIcon'
import DeferredPicture from '../DeferredPicture/DeferredPicture'
import story480 from '../../assets/photos/geschichte-480.avif'
import story694 from '../../assets/photos/geschichte-694.avif'
import storyFallback from '../../assets/photos/geschichte-480.jpg'
import { MOTION_OK, SCRUB, gsap, useGSAP } from '../../utils/motion'

function Story() {
  const sectionRef = useRef<HTMLElement>(null)

  useGSAP(
    () => {
      const mm = gsap.matchMedia(sectionRef)

      mm.add(MOTION_OK, () => {
        // Photo settles from a slight zoom.
        gsap.fromTo(
          '.story-photo img',
          { scale: 1.2 },
          {
            scale: 1,
            ease: 'none',
            scrollTrigger: {
              trigger: '.story-photo',
              start: 'top bottom',
              end: 'center center',
              scrub: SCRUB,
            },
          },
        )

        // "1908" swings in from the left around its bottom-left corner.
        gsap.fromTo(
          '.story-year',
          { xPercent: -14, rotate: -4 },
          {
            xPercent: 0,
            rotate: 0,
            ease: 'none',
            scrollTrigger: {
              trigger: '.story-content',
              start: 'top bottom',
              end: 'top 45%',
              scrub: SCRUB,
            },
          },
        )

        // The gold rule draws from left to right.
        gsap.fromTo(
          '.story-rule',
          { scaleX: 0 },
          {
            scaleX: 1,
            ease: 'none',
            scrollTrigger: {
              trigger: '.story-rule',
              start: 'top 95%',
              end: 'top 55%',
              scrub: SCRUB,
            },
          },
        )

        // The headline fills with light from left to right. The gradient
        // is only switched on here, so without motion it is plain text.
        const title = sectionRef.current?.querySelector('h2')
        title?.classList.add('is-ink')

        gsap.fromTo(
          'h2',
          { backgroundPosition: '100% 0%' },
          {
            backgroundPosition: '0% 0%',
            ease: 'none',
            scrollTrigger: {
              trigger: 'h2',
              start: 'top 75%',
              end: 'top 40%',
              scrub: SCRUB,
            },
          },
        )

        return () => title?.classList.remove('is-ink')
      })
    },
    { scope: sectionRef },
  )

  return (
    <section
      ref={sectionRef}
      className="story"
      id="geschichte"
      aria-labelledby="story-title"
    >
      <div className="container story-grid">
        <div className="story-photo">
          <DeferredPicture
            avif={`${story480} 480w, ${story694} 694w`}
            sizes="(min-width: 700px) 44vw, 92vw"
            fallback={storyFallback}
            alt="Einblick in das gemütliche Café"
            width={694}
            height={763}
          />
        </div>

        <div className="story-content">
          <p className="eyebrow story-eyebrow">Unsere Geschichte</p>

          <p className="story-year" aria-hidden="true">
            1908
          </p>

          <div className="story-rule" aria-hidden="true" />

          <h2 id="story-title">Tradition, die verbindet.</h2>

          <p className="story-description">
            Entdecke die Leidenschaft hinter unserer Konditorei – von frisch
            gebackenen Kuchen und feinem Gebäck bis zu gutem Kaffee.
          </p>

          <Link to="/about" className="text-link story-link">
            Mehr über uns
            <ArrowIcon />
          </Link>
        </div>
      </div>
    </section>
  )
}

export default Story
