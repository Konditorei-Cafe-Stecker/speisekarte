
import './ProductCard.css'

type ProductCardProps = {
  name: string
  description: string
  price: string
  image?: string
  imageAlt?: string
}

function ProductCard({
  name,
  description,
  price,
  image,
  imageAlt,
}: ProductCardProps) {
  return (
    <article className="product-card">
      <div
        className={`product-card-image ${image ? 'has-image' : 'no-image'}`}
      >
        {image ? (
          <img src={image} alt={imageAlt ?? name} />
        ) : (
          <span className="product-card-placeholder" aria-hidden="true">
            {name}
          </span>
        )}
      </div>

      <div className="product-card-content">
        <h3>{name}</h3>
        <p>{description}</p>
        <span className="product-card-price">{price}</span>
      </div>
    </article>
  )
}

export default ProductCard
