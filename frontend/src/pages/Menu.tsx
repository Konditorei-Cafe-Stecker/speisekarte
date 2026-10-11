import './Menu.css'
import ProductCard from '../components/ProductCard/ProductCard'
import tortenImage from '../assets/torten.jpg'

type Product = {
  name: string
  description: string
  price: string
  image?: string
  imageAlt?: string
}

function Menu() {
  const tortenProducts: Product[] = [
    {
      name: 'Schokoladentorte',
      description: 'Feine Schokoladentorte mit cremiger Füllung.',
      price: '4,50 €',
      image: tortenImage,
      imageAlt: 'Torte aus der Konditorei',
    },
    {
      name: 'Erdbeertorte',
      description: 'Fruchtige Erdbeertorte mit leichter Sahnecreme.',
      price: '4,50 €',
    },
    {
      name: 'Hochzeitstorte',
      description: 'Individuelle Torten für besondere Momente.',
      price: 'Auf Anfrage',
    },
  ]

  const kuchenProducts: Product[] = [
  {
    name: 'Apfelkuchen',
    description: 'Klassischer Apfelkuchen nach traditionellem Rezept.',
    price: '3,50 €',
  },
  {
    name: 'Käsekuchen',
    description: 'Cremiger Käsekuchen mit knusprigem Boden.',
    price: '3,50 €',
  },
  {
    name: 'Schokoladenkuchen',
    description: 'Saftiger Schokoladenkuchen für echte Schokoladenfans.',
    price: '3,80 €',
  },
]

const gebaeckProducts: Product[] = [
  {
    name: 'Buttercroissant',
    description: 'Frisch gebackenes Croissant aus feinem Plunderteig.',
    price: '2,20 €',
  },
  {
    name: 'Schokocroissant',
    description: 'Knuspriges Croissant mit feiner Schokoladenfüllung.',
    price: '2,50 €',
  },
  {
    name: 'Plundergebäck',
    description: 'Feines Plundergebäck aus unserer Konditorei.',
    price: '2,80 €',
  },
]

  return (
    <main>
      <section className="menu">
        <div className="menu-header">
          <p className="menu-eyebrow">UNSERE AUSWAHL</p>

          <h1>Speisekarte</h1>

          <p className="menu-description">
            Entdecke unsere handgemachten Kuchen, Torten und
            feinen Gebäckspezialitäten.
          </p>
        </div>

        {/* Torten */}
        <div className="menu-category">
          <h2>Torten</h2>

          <div className="product-grid">
            {tortenProducts.map((product) => (
              <ProductCard
                key={product.name}
                name={product.name}
                description={product.description}
                price={product.price}
                image={product.image}
                imageAlt={product.imageAlt}
              />
            ))}
          </div>
        </div>

        {/* Kuchen */}
        <div className="menu-category">
          <h2>Kuchen</h2>

          <div className="product-grid">
            {kuchenProducts.map((product) => (
                <ProductCard
                key={product.name}
                name={product.name}
                description={product.description}
                price={product.price}
                image={product.image}
                imageAlt={product.imageAlt}
                />
            ))}
          </div>
        </div>

        {/* Gebäck */}
        <div className="menu-category">
          <h2>Gebäck</h2>

          <div className="product-grid">
            {gebaeckProducts.map((product) => (
                <ProductCard
                key={product.name}
                name={product.name}
                description={product.description}
                price={product.price}
                image={product.image}
                imageAlt={product.imageAlt}
                />
            ))}
          </div>
        </div>
      </section>
    </main>
  )
}

export default Menu