import './ProductCard.css'

type ProductCardProps = {
  name: string
  description: string | null
  price: string
  sizeVolume?: string | null
  notes?: string | null
  available?: boolean
  quantity?: number
  onQuantityChange?: (quantity: number) => void
  image?: string
  imageAlt?: string
}

function ProductCard({
  name,
  description,
  price,
  sizeVolume,
  notes,
  available = true,
  quantity = 0,
  onQuantityChange,
  image,
  imageAlt,
}: ProductCardProps) {
  const productLabel = [name, sizeVolume, description]
    .filter(Boolean)
    .join(', ')

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
        {sizeVolume && <p>{sizeVolume}</p>}
        {description && <p>{description}</p>}
        {notes && <p>{notes}</p>}
        <span className="product-card-price">{price}</span>
        {!available && <p>Derzeit nicht verfügbar</p>}

        {onQuantityChange && (
          <div
            className="product-card-quantity"
            role="group"
            aria-label={`Menge für ${productLabel}`}
          >
            <button
              type="button"
              aria-label={`${productLabel}: Anzahl verringern`}
              disabled={quantity === 0}
              onClick={() => onQuantityChange(quantity - 1)}
            >
              −
            </button>

            <output aria-label="Ausgewählte Menge">
              {quantity}
            </output>

            <button
              type="button"
              aria-label={`${productLabel}: Anzahl erhöhen`}
              disabled={!available}
              onClick={() => onQuantityChange(quantity + 1)}
            >
              +
            </button>
          </div>
        )}
      </div>
    </article>
  )
}

export default ProductCard