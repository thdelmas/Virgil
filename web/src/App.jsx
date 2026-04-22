import logo from '/logo.svg'

const features = [
  {
    title: 'Fall detection',
    body:
      "Your phone's accelerometer notices a hard fall — free-fall, impact, then stillness. A 30-second countdown appears on-screen. If you don't tap \"I'm OK,\" Virgil sends your GPS location to your emergency contacts and calls the first one.",
  },
  {
    title: 'Check-in',
    body:
      'At an interval you set, Virgil quietly notices whether your phone has seen any sign of life — a screen unlock, a tap, a step. If not, it gently asks if you\u2019re OK. No response in five minutes, and it triggers the same emergency alert.',
  },
]

const principles = [
  ['Simple enough for anyone', 'No accounts. No tutorials. If you can install an app and add a phone number, you can use Virgil.'],
  ['Your data stays on your phone', 'No cloud, no servers, no analytics. Location is shared only when an alert fires, and only with the contacts you chose.'],
  ['Free and open source', 'No ads. No subscription. No premium tier. The code is open so anyone can verify what it does.'],
  ['Works everywhere', 'Any country, any language, any Android phone with an accelerometer. Alerts go out by SMS and phone call — no internet required.'],
  ['Battery-conscious', 'Efficient sensor batching and minimal wake-ups. A guardian that kills your battery is no guardian at all.'],
  ['Honest framing', 'Virgil is "help arrives faster," never "you are safe." It may miss events or trigger false alerts. Stay as careful as you would without it.'],
]

export default function App() {
  return (
    <div className="page">
      <header className="hero">
        <img src={logo} alt="Virgil lantern logo" className="logo" width="96" height="96" />
        <h1>Virgil</h1>
        <p className="tagline">Your silent guardian.</p>
        <p className="lede">
          A free, open-source Android app that watches over you when you&rsquo;re alone.
          Two features, both designed to save lives.
        </p>
        <div className="cta-row">
          <a className="cta primary" href="https://github.com/thdelmas/Virgil" rel="noreferrer">
            View on GitHub
          </a>
          <span className="cta-note">Play Store target: mid-2026</span>
        </div>
      </header>

      <section className="section">
        <h2>What it does</h2>
        <div className="grid two">
          {features.map((f) => (
            <article key={f.title} className="card">
              <h3>{f.title}</h3>
              <p>{f.body}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="section">
        <h2>Principles</h2>
        <div className="grid three">
          {principles.map(([title, body]) => (
            <article key={title} className="card subtle">
              <h3>{title}</h3>
              <p>{body}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="section">
        <h2>Who it&rsquo;s for</h2>
        <ul className="list">
          <li>An elderly parent living alone.</li>
          <li>Anyone spending long stretches at home by themselves.</li>
          <li>Someone prone to moments of reduced awareness or balance trouble.</li>
          <li>Someone hiking or working alone in a remote area.</li>
          <li>Anyone who wants a safety net without extra hardware, a subscription, or a cloud service.</li>
        </ul>
      </section>

      <section className="section disclaimer">
        <h2>Not a medical device</h2>
        <p>
          Virgil is not a medical device. It does not diagnose, monitor health, or
          talk to doctors, hospitals, or insurance companies. It is a phone app
          that notices when something might be wrong and tells the people you trust.
          It may miss events or raise false alerts — treat it as a safety net, not
          a guarantee.
        </p>
      </section>

      <footer className="footer">
        <p>
          <a href="https://github.com/thdelmas/Virgil" rel="noreferrer">GitHub</a>
          <span className="sep">·</span>
          <a href="https://github.com/thdelmas/Virgil/blob/main/MANIFESTO.md" rel="noreferrer">Manifesto</a>
          <span className="sep">·</span>
          <a href="https://github.com/thdelmas/Virgil/blob/main/ROADMAP.md" rel="noreferrer">Roadmap</a>
          <span className="sep">·</span>
          <a href="https://github.com/thdelmas/Virgil/blob/main/LICENSE" rel="noreferrer">Apache 2.0</a>
        </p>
        <p className="muted">Built with care. No tracking on this page.</p>
      </footer>
    </div>
  )
}
