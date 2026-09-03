/**
 * Prépare une pile déployée à recevoir de vrais clients.
 *
 * Fait ce qui est mécanique, vérifie ce qui ne l'est pas, et refuse ce qui
 * vous enfermerait dehors :
 *
 *   1. fait tourner le secret du client de service Keycloak — celui du dépôt
 *      est public, donc n'en est pas un
 *   2. crée un administrateur réel, si aucun n'existe encore
 *   3. supprime les comptes de démonstration, dont les mots de passe sont
 *      écrits dans le dépôt
 *   4. contrôle la documentation d'API, la boîte aux lettres de test et les
 *      sauvegardes, sans y toucher
 *
 * Rejouable sans dommage : chaque étape constate avant d'agir.
 *
 *   node scripts/durcir-production.mjs                     # constat seul
 *   node scripts/durcir-production.mjs --appliquer         # agit
 *   node scripts/durcir-production.mjs --appliquer --admin vous@exemple.ma
 *   node scripts/durcir-production.mjs --appliquer --admin vous@exemple.ma --nom "Safaa Anciri"
 */
import { readFileSync, writeFileSync, existsSync, readdirSync } from 'node:fs'
import { execSync } from 'node:child_process'
import { randomBytes } from 'node:crypto'

const APPLIQUER = process.argv.includes('--appliquer')
const EMAIL_ADMIN = process.argv[process.argv.indexOf('--admin') + 1]?.includes('@')
  ? process.argv[process.argv.indexOf('--admin') + 1] : null
/**
 * Prénom et nom de l'administrateur créé.
 *
 * Obligatoires, et pas par courtoisie : le profil utilisateur déclaratif de
 * Keycloak 26 les exige. Un compte créé sans eux se voit refuser tout jeton
 * avec « Account is not fully set up » — et « requiredActions » reste vide,
 * si bien que rien n'indique la cause. À défaut, on les tire de l'adresse.
 */
const NOM_ADMIN = process.argv.includes('--nom')
  ? process.argv[process.argv.indexOf('--nom') + 1] : null

const bleu = (t) => console.log(`\x1b[36m${t}\x1b[0m`)
const vert = (t) => console.log(`  \x1b[32m✓\x1b[0m ${t}`)
const jaune = (t) => console.log(`  \x1b[33m!\x1b[0m ${t}`)
const rouge = (t) => console.log(`  \x1b[31m✗\x1b[0m ${t}`)
const info = (t) => console.log(`    ${t}`)

/* ------------------------------------------------------------------ *
 * Environnement
 * ------------------------------------------------------------------ */
if (!existsSync('partage.env')) {
  rouge('partage.env est introuvable — lancez d’abord ./scripts/partager.sh')
  process.exit(1)
}
const env = Object.fromEntries(
  readFileSync('partage.env', 'utf8').split('\n')
    .filter((l) => l.includes('=') && !l.trimStart().startsWith('#'))
    .map((l) => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]))

const PORT = env.PORT_PUBLIC ?? '8090'
const KC = `http://localhost:${PORT}/auth`
const REALM = 'booking-realm'
/** Comptes créés par l'import du realm et par le jeu de démonstration. */
const DEMO = /^(admin|pro1|client1|pro\.|equipe\.)/

const jetonMaster = async () => {
  const r = await fetch(`${KC}/realms/master/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: 'admin-cli', username: env.KEYCLOAK_ADMIN ?? 'admin',
      password: env.KEYCLOAK_ADMIN_PASSWORD, grant_type: 'password',
    }),
  })
  if (!r.ok) throw new Error(`console Keycloak inaccessible (${r.status}) — la pile tourne-t-elle ?`)
  return (await r.json()).access_token
}

const kc = async (methode, chemin, corps) => {
  const r = await fetch(`${KC}/admin/realms/${REALM}${chemin}`, {
    method: methode,
    headers: { Authorization: `Bearer ${JETON}`, 'Content-Type': 'application/json' },
    ...(corps ? { body: JSON.stringify(corps) } : {}),
  })
  if (!r.ok && r.status !== 404) {
    throw new Error(`${methode} ${chemin} → ${r.status} ${await r.text()}`)
  }
  const texte = await r.text()
  return texte ? JSON.parse(texte) : null
}

const JETON = await jetonMaster()
console.log()
if (!APPLIQUER) jaune('Mode constat. Ajoutez --appliquer pour agir.\n')

/* ------------------------------------------------------------------ *
 * 1. Le secret du client de service
 * ------------------------------------------------------------------ */
bleu('→ Secret du client de service')
const clients = await kc('GET', '/clients?clientId=booking-backend-admin')
const client = clients?.[0]

if (!client) {
  rouge('le client booking-backend-admin est absent du realm')
} else {
  const actuel = (await kc('GET', `/clients/${client.id}/client-secret`))?.value
  const publie = 'secret-de-developpement-a-remplacer'
  if (actuel !== publie) {
    vert('déjà différent de celui du dépôt')
  } else if (!APPLIQUER) {
    rouge('identique à celui publié sur GitHub — donc connu de tous')
    info('un jeton de service avec manage-users s’obtient avec ce seul secret')
  } else {
    const nouveau = (await kc('POST', `/clients/${client.id}/client-secret`))?.value
    if (!nouveau) throw new Error('Keycloak n’a pas renvoyé de nouveau secret')

    // Écrit dans partage.env, qui n'est pas suivi par Git. La valeur du
    // fichier de realm devient inopérante : elle ne sert qu'au premier import.
    const contenu = readFileSync('partage.env', 'utf8')
    writeFileSync('partage.env', contenu.includes('KEYCLOAK_ADMIN_CLIENT_SECRET=')
      ? contenu.replace(/^KEYCLOAK_ADMIN_CLIENT_SECRET=.*$/m, `KEYCLOAK_ADMIN_CLIENT_SECRET=${nouveau}`)
      : `${contenu.trimEnd()}\nKEYCLOAK_ADMIN_CLIENT_SECRET=${nouveau}\n`)
    // La copie en mémoire est mise à jour, pas seulement le fichier : c'est
    // elle qui sera passée à docker compose en fin de script. Sans cela, la
    // relance repartait avec l'ancien secret et l'API ne pouvait plus créer
    // de comptes — sans que rien ne le signale avant le premier essai.
    env.KEYCLOAK_ADMIN_CLIENT_SECRET = nouveau
    vert('nouveau secret généré et écrit dans partage.env')
    jaune('l’API doit être relancée pour le lire — fait en fin de script')
  }
}

/* ------------------------------------------------------------------ *
 * 2. Un administrateur réel
 *
 * Créé avant toute suppression : retirer « admin » sans successeur ferme la
 * porte, et la rouvrir demande alors la console du realm master.
 * ------------------------------------------------------------------ */
console.log()
bleu('→ Administrateur réel')
const tous = await kc('GET', '/users?max=500') ?? []
const roleAdmin = await kc('GET', '/roles/admin')

const aRoleAdmin = async (id) =>
  ((await kc('GET', `/users/${id}/role-mappings/realm`)) ?? []).some((r) => r.name === 'admin')

const admins = []
for (const u of tous) if (await aRoleAdmin(u.id)) admins.push(u)
const adminsReels = admins.filter((u) => !DEMO.test(u.username))

if (adminsReels.length > 0) {
  vert(`${adminsReels.length} administrateur(s) hors démonstration : ${adminsReels.map((u) => u.username).join(', ')}`)
} else if (!EMAIL_ADMIN) {
  rouge('aucun administrateur réel — seul le compte de démonstration « admin » existe')
  info('relancez avec : --appliquer --admin vous@votre-domaine.ma')
} else if (!APPLIQUER) {
  jaune(`créerait un administrateur pour ${EMAIL_ADMIN}`)
} else {
  const [prenomDefaut, ...resteDefaut] = (NOM_ADMIN
    ?? EMAIL_ADMIN.split('@')[0].replace(/[._-]+/g, ' ')).trim().split(/\s+/)
  const prenom = prenomDefaut.charAt(0).toUpperCase() + prenomDefaut.slice(1)
  const nom = resteDefaut.join(' ') || 'Administration'

  await kc('POST', '/users', {
    username: EMAIL_ADMIN, email: EMAIL_ADMIN, enabled: true,
    firstName: prenom, lastName: nom,
    // Vérifié d'office : l'action requise VERIFY_EMAIL s'interposerait avant
    // le choix du mot de passe et bloquerait la première connexion.
    emailVerified: true, attributes: { locale: ['fr'] },
  })
  const cree = (await kc('GET', `/users?username=${encodeURIComponent(EMAIL_ADMIN)}&exact=true`))?.[0]
  if (!cree) throw new Error('compte administrateur introuvable après création')
  await kc('POST', `/users/${cree.id}/role-mappings/realm`, [{ id: roleAdmin.id, name: 'admin' }])
  vert(`administrateur créé : ${prenom} ${nom} <${EMAIL_ADMIN}>`)

  // Aucun mot de passe n'est fixé ici : il le choisit par lien, comme les
  // gérants. Un mot de passe transmis par un tiers finit toujours écrit
  // quelque part.
  const r = await fetch(
    `${KC}/admin/realms/${REALM}/users/${cree.id}/execute-actions-email`
    + `?client_id=booking-app&redirect_uri=${encodeURIComponent(`${env.URL_PUBLIQUE}/admin`)}`,
    { method: 'PUT', headers: { Authorization: `Bearer ${JETON}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(['UPDATE_PASSWORD']) })
  if (r.ok) vert('e-mail envoyé pour qu’il choisisse son mot de passe')
  else jaune(`e-mail non envoyé (${r.status}) — définissez le mot de passe depuis la console`)
  adminsReels.push(cree)
}

/* ------------------------------------------------------------------ *
 * 3. Les comptes de démonstration
 * ------------------------------------------------------------------ */
console.log()
bleu('→ Comptes de démonstration')
const aSupprimer = tous.filter((u) => DEMO.test(u.username))

if (aSupprimer.length === 0) {
  vert('aucun compte de démonstration')
} else if (adminsReels.length === 0) {
  rouge(`${aSupprimer.length} compte(s) à supprimer, mais aucun administrateur réel pour prendre la suite`)
  info('on ne touche à rien : supprimer « admin » sans successeur ferme la porte')
} else if (!APPLIQUER) {
  jaune(`${aSupprimer.length} compte(s) à supprimer, mot de passe égal à l’identifiant :`)
  info(aSupprimer.map((u) => u.username).join(', '))
} else {
  for (const u of aSupprimer) {
    await kc('DELETE', `/users/${u.id}`)
    vert(`supprimé : ${u.username}`)
  }
  jaune('les salons de démonstration restent en base, sans propriétaire — à retirer si besoin')
}

/* ------------------------------------------------------------------ *
 * 4. Contrôles sans action
 * ------------------------------------------------------------------ */
console.log()
bleu('→ Contrôles')

/*
 * On lit le réglage, pas la réponse HTTP.
 *
 * À travers le proxy, /api/v3/api-docs répond 401 même documentation
 * activée : les chemins sont préfixés /api et les règles permitAll ne
 * correspondent plus. Conclure « fermée » sur ce 401 était une erreur de
 * lecture — c'est un accident de préfixe, qu'un changement de routage
 * défait. Le réglage de l'application est la seule source fiable.
 */
let doc = null
try {
  doc = execSync('docker compose -f docker-compose.partage.yml exec -T api printenv OPENAPI_ACTIF',
    { encoding: 'utf8', env: { ...process.env, ...env }, stdio: ['ignore', 'pipe', 'ignore'] }).trim()
} catch {
  // printenv sort en erreur quand la variable est absente : c'est alors la
  // valeur par défaut de l'application qui s'applique, et elle est « false ».
  doc = 'absente (défaut : false)'
}
if (doc === 'true') rouge('documentation d’API ACTIVE — passez OPENAPI_ACTIF à false')
else vert(`documentation d’API désactivée (OPENAPI_ACTIF=${doc})`)

/*
 * Le serveur d'envoi effectivement utilisé.
 *
 * Ce contrôle ne peut pas être une garde de configuration : Compose interpole
 * chaque fichier avant de les fusionner, donc un « ${MAIL_HOST:?} » dans la
 * pile de base refuserait la valeur que le complément de démonstration
 * fournit. Il est donc vérifié ici, sur le conteneur en marche.
 */
let poste = ''
try {
  poste = execSync('docker compose -f docker-compose.partage.yml exec -T api printenv MAIL_HOST',
    { encoding: 'utf8', env: { ...process.env, ...env }, stdio: ['ignore', 'pipe', 'ignore'] }).trim()
} catch { poste = '' }

if (!poste || poste === 'mailpit' || poste === 'localhost') {
  rouge(`aucun serveur d’envoi réel (MAIL_HOST=${poste || 'vide'})`)
  info('confirmations, rappels J-1 et invitations de gérants ne partent nulle part')
  info('renseigner MAIL_HOST et compagnie dans partage.env, avec SPF et DKIM sur le domaine')
} else {
  vert(`serveur d’envoi : ${poste}`)
}

/*
 * Mailpit reconnu à son API, non à un code 200.
 *
 * Sans la route, /courrier/ retombe sur le repli du site : index.html avec un
 * 200 tout à fait normal. Conclure « exposée » là-dessus se déclenchait pour
 * rien. Son API JSON, elle, n'existe que si Mailpit est réellement branché.
 */
const courrier = await fetch(`http://localhost:${PORT}/courrier/api/v1/messages`)
  .then((r) => r.ok && r.headers.get('content-type')?.includes('json'))
  .catch(() => false)
if (courrier) {
  rouge('la boîte aux lettres de test est exposée sur /courrier')
  info('elle contient les liens d’invitation : quiconque a l’adresse prend un compte gérant')
  info('retirer le bloc « handle /courrier* » du Caddyfile, et brancher un vrai SMTP')
} else vert('boîte aux lettres de test non exposée')

const sauvegardes = existsSync('sauvegardes')
  ? readdirSync('sauvegardes').filter((n) => n.startsWith('20')) : []
if (sauvegardes.length === 0) {
  rouge('aucune sauvegarde — ./scripts/sauvegarder.sh')
} else {
  const derniere = sauvegardes.sort().at(-1)
  const jours = Math.floor((Date.now() - Date.parse(derniere.slice(0, 10))) / 86400000)
  if (jours > 1) jaune(`dernière sauvegarde il y a ${jours} jours (${derniere}) — automatisez-la`)
  else vert(`${sauvegardes.length} sauvegarde(s), la dernière du ${derniere}`)
}

/* ------------------------------------------------------------------ */
console.log()
if (APPLIQUER) {
  bleu('→ Relance de l’API pour lire le nouveau secret')
  try {
    execSync('docker compose -f docker-compose.partage.yml up -d api',
      { stdio: 'ignore', env: { ...process.env, ...env } })
    vert('API relancée')
  } catch {
    jaune('relance manuelle : docker compose -f docker-compose.partage.yml up -d api')
  }
  console.log()
  vert('durcissement appliqué')
} else {
  jaune('Rien n’a été modifié. Relancez avec --appliquer.')
}
