import './Tortenservice.css'

function Tortenservice() {
  return (
    <main>
      <section className="tortenservice">
        <div className="tortenservice-header">
          <p className="tortenservice-eyebrow">TORTENSERVICE</p>

          <h1>
            Deine Torte.
            <br />
            Dein besonderer Moment.
          </h1>

          <p className="tortenservice-description">
            Wir fertigen individuelle Torten für Geburtstage,
            Hochzeiten und andere besondere Anlässe.
          </p>
        </div>

        <div className="tortenservice-content">
          <h2>Individuelle Torten nach deinen Wünschen</h2>

          <p>
            Gemeinsam gestalten wir eine Torte, die zu deinem Anlass
            passt – von der Größe über die Geschmacksrichtung bis zur
            Dekoration.
          </p>

          <button> Torte anfragen → </button>
        </div>
      </section>
    </main>
  )
}

export default Tortenservice