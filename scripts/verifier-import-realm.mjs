/**
 * Le realm s'importe-t-il, et donne-t-il le rôle « client » ?
 *
 * Deux questions qu'aucun test ne posait, et qui se sont toutes deux révélées
 * fausses le même jour.
 *
 * La première : `booking-realm-realm.json` n'est pas seulement du JSON valide,
 * c'est un document que Keycloak doit accepter. Déclarer un rôle composite qui
 * cite un rôle absent de l'import — `uma_authorization`, que Keycloak crée
 * lui-même, mais après avoir résolu les composites — fait échouer le
 * démarrage, pas seulement l'import. Le serveur sort avec « Unable to find
 * composite realm role », et une installation neuve ne démarre jamais. Rien ne
 * l'aurait signalé avant le premier déploiement.
 *
 * La seconde : le rôle « client » doit figurer dans les composites du rôle par
 * défaut, sans quoi un compte créé par le formulaire d'inscription peut se
 * connecter mais se fait refuser par chaque route authentifiée. C'est arrivé.
 *
 * Le test part d'un Keycloak vierge et jetable, sur un port à part, pour que
 * la réponse porte sur l'import et non sur l'état d'un realm déjà en service —
 * les installations qui tournent ne réimportent pas.
 *
 *   node scripts/verifier-import-realm.mjs
 */
import { execSync, spawnSync } from 'node:child_process'

const IMAGE = process.env.KC_IMAGE ?? 'quay.io/keycloak/keycloak:26.3.5'
const PORT = process.env.KC_PORT_ESSAI ?? '8099'
const CONTENEUR = 'booking-verif-import-realm'
const REALM = 'booking-realm'
const KC = `http://localhost:${PORT}`

const ok = (c) => (c ? '✅' : '❌')
let echecs = 0
const dire = (condition, libelle) => {
  if (!condition) echecs += 1
  console.log(' ', ok(condition), libelle)
  return condition
}
const pause = (ms) => new Promise((r) => setTimeout(r, ms))
const sh = (c) => execSync(c, { stdio: 'pipe' }).toString().trim()

const nettoyer = () => spawnSync('docker', ['rm', '-f', CONTENEUR], { stdio: 'ignore' })

process.on('exit', nettoyer)
process.on('SIGINT', () => { nettoyer(); process.exit(130) })

console.log()
console.log(`─── Import du realm sur un Keycloak vierge (${IMAGE}) ───`)
nettoyer()

sh(`docker run -d --name ${CONTENEUR} -p ${PORT}:8080`
  + ' -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin'
  + ` -v "${process.cwd()}/keycloak:/opt/keycloak/data/import:ro"`
  + ` ${IMAGE} start-dev --import-realm`)

/*
 * On attend le realm, pas le conteneur.
 *
 * Un échec d'import fait sortir le serveur : la boucle doit donc surveiller
 * l'état du conteneur autant que la réponse HTTP, sinon elle patiente jusqu'au
 * bout de son délai sur un processus déjà mort et rend un message trompeur.
 */
let pret = false
let vivant = true
for (let i = 0; i < 40; i += 1) {
  await pause(3000)
  const etat = sh(`docker inspect --format '{{.State.Status}}' ${CONTENEUR}`)
  if (etat !== 'running') { vivant = false; break }
  try {
    const r = await fetch(`${KC}/realms/${REALM}`)
    if (r.ok) { pret = true; break }
  } catch { /* pas encore à l'écoute */ }
}

if (!dire(vivant, 'le serveur démarre — l’import du realm ne le fait pas sortir')) {
  const journal = sh(`docker logs ${CONTENEUR} 2>&1 | grep -iE 'ERROR|Unable' | head -5`)
  journal.split('\n').forEach((l) => console.log('     ', l.slice(0, 160)))
  process.exit(1)
}
dire(pret, `le realm ${REALM} répond`)

/* ------------------------------------------------------------------ *
 * Le rôle par défaut
 * ------------------------------------------------------------------ */
const jeton = (await (await fetch(`${KC}/realms/master/protocol/openid-connect/token`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({
    client_id: 'admin-cli', username: 'admin', password: 'admin', grant_type: 'password',
  }),
})).json()).access_token

const admin = async (methode, chemin, corps) => {
  const r = await fetch(`${KC}/admin/realms/${REALM}${chemin}`, {
    method: methode,
    headers: { Authorization: `Bearer ${jeton}`, 'Content-Type': 'application/json' },
    ...(corps ? { body: JSON.stringify(corps) } : {}),
  })
  const texte = await r.text()
  return { statut: r.status, corps: texte ? JSON.parse(texte) : null }
}

console.log()
console.log('─── Le rôle par défaut ─────────────────────────────')
const composites = (await admin('GET', `/roles/default-roles-${REALM}/composites`)).corps ?? []
const noms = composites.map((r) => r.name)
dire(noms.includes('client'),
  `« client » figure dans le rôle par défaut (${noms.sort().join(', ')})`)
// Les rôles de gestion de compte de Keycloak doivent survivre : les déclarer
// nous-mêmes dans l'import risquait précisément de les remplacer.
dire(['manage-account', 'view-profile', 'offline_access', 'uma_authorization']
  .every((r) => noms.includes(r)),
  'les rôles de gestion de compte de Keycloak sont préservés')

/* ------------------------------------------------------------------ *
 * Un compte créé sans rôle explicite
 * ------------------------------------------------------------------ */
console.log()
console.log('─── Un compte sans rôle explicite ─────────────────')
const EMAIL = 'essai.import@example.ma'
const MDP = 'Essai-Import-2026!'
await admin('POST', '/users', {
  username: EMAIL, email: EMAIL, firstName: 'Essai', lastName: 'Import',
  enabled: true, emailVerified: true,
  credentials: [{ type: 'password', value: MDP, temporary: false }],
})
const trouves = (await admin('GET', `/users?username=${encodeURIComponent(EMAIL)}`)).corps ?? []
if (dire(trouves.length === 1, 'le compte est créé')) {
  const roles = ((await admin('GET', `/users/${trouves[0].id}/role-mappings/realm`)).corps ?? [])
    .map((r) => r.name)
  dire(!roles.includes('client'),
    'aucun rôle « client » ne lui est attribué directement — il vient du défaut')

  // La question qui compte : le rôle arrive-t-il jusqu'au jeton ? Un rôle
  // présent en base mais absent du jeton donnerait le même « Accès refusé ».
  const r = await fetch(`${KC}/realms/${REALM}/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: 'booking-app', grant_type: 'password',
      username: EMAIL, password: MDP, scope: 'openid',
    }),
  })
  if (dire(r.ok, `il obtient un jeton (HTTP ${r.status})`)) {
    const charge = JSON.parse(
      Buffer.from((await r.json()).access_token.split('.')[1], 'base64url').toString())
    const dansLeJeton = charge.realm_access?.roles ?? []
    dire(dansLeJeton.includes('client'),
      `son jeton porte « client » (${dansLeJeton.sort().join(', ')})`)
  }
}

nettoyer()
console.log()
console.log(echecs === 0
  ? '✅ Une installation neuve démarre et donne le rôle client.'
  : `❌ ${echecs} assertion(s) en échec.`)
process.exit(echecs === 0 ? 0 : 1)
