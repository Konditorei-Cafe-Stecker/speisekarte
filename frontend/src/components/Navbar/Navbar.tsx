import './Navbar.css'

function Navbar() {
  return (
    <nav className="navbar">
      <div className="navbar-logo">
        Stecker Konditorei
      </div>

      <div className="navbar-links">
        <a href="/">Home</a>
        <a href="/menu">Speisekarte</a>
        <a href="/tortenservice">Tortenservice</a>
        <a href="/about">Über uns</a>
      </div>
    </nav>
  )
}

export default Navbar