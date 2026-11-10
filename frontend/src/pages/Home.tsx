import './Home.css'
import Hero from '../components/Hero/Hero'
import Marquee from '../components/Marquee/Marquee'
import Featured from '../components/Featured/Featured'
import GiantLines from '../components/GiantLines/GiantLines'
import Story from '../components/Story/Story'
import MenuTeaser from '../components/MenuTeaser/MenuTeaser'
import Visit from '../components/Visit/Visit'
import { useScrollToHashOnMount } from '../utils/scroll'
import {
  MOTION_OK,
  SCRUB,
  ScrollTrigger,
  gsap,
  useGSAP,
} from '../utils/motion'
import { useSmoothScroll } from '../utils/smoothScroll'

function Home() {
  useSmoothScroll()
  useScrollToHashOnMount()

  // The header and footer belong to the whole site, but their scroll
  // animations exist only on the Startseite, so they are driven from here.
  useGSAP(() => {
    const mm = gsap.matchMedia()

    mm.add(MOTION_OK, () => {
      // Progress line under the header: grows with the page scroll.
      gsap.fromTo(
        '.navbar-progress',
        { scaleX: 0 },
        {
          scaleX: 1,
          ease: 'none',
          scrollTrigger: { start: 0, end: 'max', scrub: true },
        },
      )

      // Footer logo grows into place from its bottom-left corner.
      gsap.fromTo(
        '.site-footer-logo',
        { y: 90, scale: 0.82, transformOrigin: 'left bottom' },
        {
          y: 0,
          scale: 1,
          ease: 'none',
          scrollTrigger: {
            trigger: '.site-footer',
            start: 'top bottom',
            end: 'bottom bottom',
            scrub: SCRUB,
          },
        },
      )
    })

    // Web fonts change text heights after the first render; measure the
    // scroll positions again once they are in. (ScrollTrigger already does
    // the same by itself when the window has finished loading.)
    let active = true

    document.fonts.ready.then(() => {
      if (active) ScrollTrigger.refresh()
    })

    return () => {
      active = false
    }
  })

  return (
    <div className="home">
      <Hero />
      <Marquee />
      <Featured />
      <GiantLines />
      <Story />
      <MenuTeaser />
      <Visit />
    </div>
  )
}

export default Home
