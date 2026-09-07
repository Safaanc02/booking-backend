/**
 * Donne le rôle « client » aux comptes créés par le formulaire d'inscription.
 *
 * L'inscription était ouverte (`registrationAllowed`) mais le realm n'avait
 * aucun rôle métier par défaut : Keycloak ne place d'office que ses propres
 * rôles de gestion de compte — manage-account, view-profile, offline_access,
 * uma_authorization. Un compte créé par le formulaire pouvait donc se
 * connecter, le bandeau affichait son nom, et chaque route authentifiée le
 * refusait. « Accès refusé » sur sa propre page de réservations.
 *
 * L'import du realm est corrigé pour les installations neuves. Ce script
 * répare celles qui tournent déjà, où le realm ne sera pas réimporté :
 *
 *   1. ajoute « client » aux composites du rôle par défaut du realm, pour que
 *      les inscriptions suivantes soient correctes ;
 *   2. attribue « client » aux comptes existants dépourvus de tout rôle
 *      métier — ceux que le défaut a laissés inutilisables.
 *
 * Ne touche pas aux comptes qui ont déjà client, pro ou admin : un
 * professionnel n'a pas besoin qu'on lui ajoute quoi que ce soit, et un
 * compte de service n'a rien à voir avec le rôle client.
 *
 * Rejouable sans dommage : chaque étape constate avant d'agir.
 *
 *   node scripts/reparer-roles-client.mjs                  # constat seul
 *   node scripts/reparer-roles-client.mjs --appliquer      # agit
 *
 * Adresses pilotables par l'environnement, pour viser la pile partagée comme
 * le Keycloak de développement :
 *
 *   KC_URL=http://localhost:8081 KC_ADMIN=admin KC_ADMIN_PASSWORD=admin \
 *     node scripts/reparer-roles-client.mjs --appliquer
 */
import { readFileSync, existsSync } from 'node:fs'

const APPLIQUER = process.argv.includes('--appliquer')

const bleu = (t) => console.log(`\x1b[36m${t}\x1b[0m`)
const vert = (t) => console.log(`  \x1b[32m✓\x1b[0m ${t}`)
const jaune = (t) => console.log(`  \x1b[33m!\x1b[0m ${t}`)
const rouge = (t) => console.log(`  \x1b[31m✗\x1b[0m ${t}`)
const info = (t) => console.log(`    ${t}`)

/* ------------------------------------------------------------------ *
 * Environnement
 * ------------------------------------------------------------------ */
/**
 * partage.env sert de source par défaut quand il existe, pour viser la pile
 * partagée sans rien retaper. Les variables d'environnement le supplantent :
 * c'est ainsi qu'on répare le Keycloak de développement, qui n'a pas ce
 * fichier.
 */
const env = existsSync('partage.env')
  ? Object.fromEntries(
      readFileSync('partage.env', 'utf8').split('\n')
        .filter((l) => l.includes('=') && !l.trimStart().startsWith('#'))
        .map((l) => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]))
  : {}

const KC = process.env.KC_URL
  ?? `http://localhost:${env.PORT_PUBLIC ?? '8090'}/auth`
const IDENTIFIANT = process.env.KC_ADMIN ?? env.KEYCLOAK_ADMIN ?? 'admin'
const MOT_DE_PASSE = process.env.KC_ADMIN_PASSWORD ?? env.KEYCLOAK_ADMIN_PASSWORD ?? 'admin'
const REALM = process.env.KC_REALM ?? 'booking-realm'
const ROLE_DEFAUT = `default-roles-${REALM}`

/** Les trois rôles du domaine. Avoir l'un d'eux suffit à être servi par l'API. */
const ROLES_METIER = ['client', 'pro', 'admin']

const jetonMaster = async () => {
  const r = await fetch(`${KC}/realms/master/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: 'admin-cli', username: IDENTIFIANT,
      password: MOT_DE_PASSE, grant_type: 'password',
    }),
  })
  if (!r.ok) {
    throw new Error(`console Keycloak inaccessible sur ${KC} (${r.status})`
      + ' — la pile tourne-t-elle, et les identifiants sont-ils les bons ?')
  }
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

let JETON
try {
  JETON = await jetonMaster()
} catch (e) {
  rouge(e.message)
  process.exit(1)
}

console.log()
bleu(`Rôle « client » — ${KC}, realm ${REALM}`)
if (!APPLIQUER) info('Constat seul. Ajoutez --appliquer pour agir.')
console.log()

/* ------------------------------------------------------------------ *
 * 1. Le rôle par défaut du realm
 * ------------------------------------------------------------------ */
bleu('1. Rôle par défaut, pour les inscriptions à venir')

const roleClient = await kc('GET', '/roles/client')
if (!roleClient) {
  rouge('le rôle « client » n’existe pas dans ce realm — import incomplet ?')
  process.exit(1)
}

const composites = (await kc('GET', `/roles/${ROLE_DEFAUT}/composites`)) ?? []
const dejaDedans = composites.some((r) => r.name === 'client')

if (dejaDedans) {
  vert(`« client » figure déjà dans ${ROLE_DEFAUT}`)
} else {
  jaune(`« client » manque dans ${ROLE_DEFAUT}`)
  info(`présents : ${composites.map((r) => r.name).join(', ') || '(aucun)'}`)
  info('conséquence : chaque compte créé par le formulaire est refusé par l’API')
  if (APPLIQUER) {
    await kc('POST', `/roles-by-id/${(await kc('GET', `/roles/${ROLE_DEFAUT}`)).id}/composites`,
      [{ id: roleClient.id, name: 'client', clientRole: false, containerId: REALM }])
    vert('« client » ajouté au rôle par défaut')
  }
}

/* ------------------------------------------------------------------ *
 * 2. Les comptes déjà créés
 * ------------------------------------------------------------------ */
console.log()
bleu('2. Comptes existants sans aucun rôle métier')

/*
 * Toutes les pages, et non les cent premiers comptes.
 *
 * Keycloak plafonne /users à cent sans paramètre. Un réparateur qui s'arrête
 * silencieusement à la centième ligne laisse le problème intact pour les
 * suivants, et personne ne s'en aperçoit avant qu'un client se plaigne.
 */
const comptes = []
for (let debut = 0; ; debut += 100) {
  const lot = await kc('GET', `/users?first=${debut}&max=100`) ?? []
  comptes.push(...lot)
  if (lot.length < 100) break
}
info(`${comptes.length} compte(s) dans le realm`)

const aReparer = []
for (const compte of comptes) {
  const roles = (await kc('GET', `/users/${compte.id}/role-mappings/realm`)) ?? []
  const metier = roles.filter((r) => ROLES_METIER.includes(r.name)).map((r) => r.name)
  if (metier.length === 0) aReparer.push(compte)
}

if (aReparer.length === 0) {
  vert('aucun compte orphelin : tous ont client, pro ou admin')
} else {
  jaune(`${aReparer.length} compte(s) sans rôle métier`)
  aReparer.forEach((c) => info(`· ${c.username}${c.email && c.email !== c.username ? ` (${c.email})` : ''}`))
  if (APPLIQUER) {
    for (const compte of aReparer) {
      await kc('POST', `/users/${compte.id}/role-mappings/realm`,
        [{ id: roleClient.id, name: 'client', clientRole: false, containerId: REALM }])
      vert(`« client » attribué à ${compte.username}`)
    }
    console.log()
    info('Ces comptes doivent se déconnecter puis se reconnecter :')
    info('un rôle n’apparaît que dans un jeton émis après son attribution.')
  }
}

console.log()
if (!APPLIQUER && (!dejaDedans || aReparer.length > 0)) {
  jaune('Rien n’a été modifié. Relancez avec --appliquer.')
} else if (APPLIQUER) {
  vert('Terminé.')
}
console.log()
