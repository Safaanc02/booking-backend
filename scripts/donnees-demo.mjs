/**
 * Jeu de données de démonstration.
 *
 * Quatre salons validés dans trois villes, un cinquième en attente de
 * validation, leurs équipes, catalogues et horaires, puis des rendez-vous
 * passés, des réservations à venir et quelques avis.
 *
 *   node scripts/donnees-demo.mjs
 *
 * Suppose la stack démarrée et l'API sur 8080. À lancer sur une base fraîche
 * (voir « Remise à zéro » dans le README) : le script ne nettoie rien.
 *
 * Tout passe par l'API, jamais par des INSERT directs : le jeu de données ne
 * peut donc ni contredire les règles métier, ni dériver du schéma.
 *
 * Écrit en JavaScript et non en shell : la première version enchaînait bash et
 * python3, et le moindre échec se perdait dans des problèmes de quoting au
 * lieu de remonter un message utile.
 */

const API = process.env.API_URL ?? 'http://localhost:8080'
const KC = process.env.KC_URL ?? 'http://localhost:8081'

const bleu = (t) => console.log(`\x1b[36m${t}\x1b[0m`)
const vert = (t) => console.log(`  \x1b[32m✓\x1b[0m ${t}`)

/* ------------------------------------------------------------------ */

const jeton = async (identifiant) => {
  const r = await fetch(`${KC}/realms/booking-realm/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: 'booking-app', username: identifiant, password: identifiant, grant_type: 'password',
    }),
  })
  if (!r.ok) throw new Error(`Keycloak : authentification de ${identifiant} refusée (${r.status})`)
  return (await r.json()).access_token
}

/** Remonte le message d'erreur de l'API tel quel : c'est lui qui est utile. */
const appel = async (methode, chemin, corps, token) => {
  const r = await fetch(`${API}${chemin}`, {
    method: methode,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(corps ? { body: JSON.stringify(corps) } : {}),
  })
  const reponse = r.status === 204 ? null : await r.json().catch(() => null)
  if (!r.ok) {
    const detail = reponse?.message ?? JSON.stringify(reponse)
    throw new Error(`${methode} ${chemin} → ${r.status} : ${detail}`)
  }
  return reponse
}

/* ------------------------------------------------------------------ */

/**
 * Crée un compte professionnel dans Keycloak et lui donne le rôle `pro`.
 *
 * Nécessaire parce qu'il n'existe aucun parcours d'inscription
 * professionnelle : un compte créé par le formulaire public n'obtient aucun
 * rôle métier et retombe sur CLIENT, donc ne peut pas référencer de salon.
 *
 * Chaque salon a ainsi son propre propriétaire. Une version précédente créait
 * tout avec le seul compte pro1 : Karim Benali se retrouvait à la tête d'un
 * barbier à Casablanca, d'un salon de coiffure à Marrakech, d'une onglerie et
 * d'un hammam à Rabat. Le multi-salon existe pour les enseignes, pas pour
 * produire ça.
 *
 * Mot de passe identique à l'identifiant, comme les autres comptes de démo.
 */
const jetonAdminKeycloak = async () => {
  const r = await fetch(`${KC}/realms/master/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: 'admin-cli',
      username: process.env.KEYCLOAK_ADMIN ?? 'admin',
      password: process.env.KEYCLOAK_ADMIN_PASSWORD ?? 'admin',
      grant_type: 'password',
    }),
  })
  if (!r.ok) {
    throw new Error(`Console Keycloak : authentification refusée (${r.status}). `
      + 'Vérifier KEYCLOAK_ADMIN et KEYCLOAK_ADMIN_PASSWORD.')
  }
  return (await r.json()).access_token
}

const kcAdmin = await jetonAdminKeycloak()

const kc = async (methode, chemin, corps) => {
  const r = await fetch(`${KC}/admin/realms/booking-realm${chemin}`, {
    method: methode,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${kcAdmin}` },
    ...(corps ? { body: JSON.stringify(corps) } : {}),
  })
  if (!r.ok && r.status !== 409) {
    throw new Error(`${methode} ${chemin} → ${r.status} : ${await r.text()}`)
  }
  return r
}

const roleProKeycloak = await (await fetch(`${KC}/admin/realms/booking-realm/roles/pro`, {
  headers: { Authorization: `Bearer ${kcAdmin}` },
})).json()

/**
 * Crée le compte s'il n'existe pas, puis renvoie un jeton applicatif.
 *
 * Le premier appel authentifié est délibéré : l'application ne garde un miroir
 * local d'un compte Keycloak qu'après une première requête authentifiée. Sans
 * lui, impossible de rattacher ce compte à une fiche d'équipe — le
 * rattachement se fait par email sur le miroir.
 */
const compteProfessionnel = async ({ identifiant, prenom, nom, email }) => {
  await kc('POST', '/users', {
    username: identifiant, email, firstName: prenom, lastName: nom,
    enabled: true, emailVerified: true,
    credentials: [{ type: 'password', value: identifiant, temporary: false }],
  })

  const trouves = await (await fetch(
    `${KC}/admin/realms/booking-realm/users?username=${encodeURIComponent(identifiant)}&exact=true`,
    { headers: { Authorization: `Bearer ${kcAdmin}` } })).json()
  const id = trouves[0]?.id
  if (!id) throw new Error(`compte ${identifiant} introuvable après création`)

  await kc('POST', `/users/${id}/role-mappings/realm`,
    [{ id: roleProKeycloak.id, name: roleProKeycloak.name }])

  const token = await jeton(identifiant)
  // Matérialise le miroir local : sans cette requête, le compte est inconnu
  // de l'application et ne peut pas être rattaché à une fiche d'équipe.
  await appel('GET', '/api/reservations/me', null, token)
  return token
}

const ADMIN = 'admin', PRO = 'pro1', CLIENT = 'client1'
bleu('→ Authentification')
const t = {
  [ADMIN]: await jeton(ADMIN),
  [PRO]: await jeton(PRO),
  [CLIENT]: await jeton(CLIENT),
}
vert('admin, pro1 et client1')

/** Semaine type : 09h-12h et 14h-19h, du lundi au samedi. */
const SEMAINE = [1, 2, 3, 4, 5, 6].flatMap((jour) => [
  { jourSemaine: jour, heureDebut: '09:00:00', heureFin: '12:00:00' },
  { jourSemaine: jour, heureDebut: '14:00:00', heureFin: '19:00:00' },
])

/**
 * Installe un salon complet : fiche, catalogue, équipe, affectations, horaires.
 * `valider` à false laisse le salon EN_ATTENTE, pour pouvoir exercer l'écran
 * d'administration.
 */
const installer = async ({ fiche, prestations, equipe, valider = true, proprietaire }) => {
  // Le compte est créé ici avec un mot de passe connu — les suites de
  // vérification se connectent en tant que ce professionnel. Le référencement
  // le retrouvera par son adresse au lieu d'en créer un second.
  const gerant = proprietaire ?? { identifiant: PRO, email: 'pro1@booking.ma',
                                   prenom: 'Karim', nom: 'Benali' }
  const tokenPro = proprietaire ? await compteProfessionnel(proprietaire) : t[PRO]

  // Le salon passe par le référencement, comme en production : c'est
  // l'administration qui l'installe pour le compte du gérant. Le script
  // n'a plus de chemin de création qui lui soit propre.
  const { salon } = await appel('POST', '/api/admin/salons', {
    salon: fiche,
    proprietaire: {
      prenom: gerant.prenom, nom: gerant.nom, email: gerant.email,
      telephone: fiche.telephone,
    },
    validerImmediatement: valider,
  }, t[ADMIN])

  const parNom = {}
  for (const p of prestations) {
    parNom[p.nom] = await appel('POST', `/api/prestations/salon/${salon.id}`, p, tokenPro)
  }

  const employes = []
  for (const membre of equipe) {
    const { fait, compte, ...profil } = membre
    // Un membre d'équipe doté d'un compte obtient des droits : PRATICIEN voit
    // son planning, GESTIONNAIRE administre le salon comme le propriétaire.
    if (compte) {
      await compteProfessionnel(compte)
      profil.email = compte.email
    }
    const e = await appel('POST', `/api/pro/salons/${salon.id}/employes`, profil, tokenPro)
    await appel('PUT', `/api/pro/employes/${e.id}/prestations`,
      { prestationIds: fait.map((nom) => parNom[nom].id) }, tokenPro)
    employes.push(e)
  }

  await appel('PUT', `/api/pro/salons/${salon.id}/horaires`, SEMAINE, tokenPro)

  const etat = valider ? 'en ligne' : 'EN ATTENTE de validation'
  const qui = gerant.identifiant
  const equipeDecrite = employes
    .map((e) => e.role === 'GESTIONNAIRE' ? `${e.prenom} (gestionnaire)` : e.prenom)
    .join(', ')
  vert(`${fiche.nom} — ${fiche.ville} · ${prestations.length} prestations · `
     + `${equipeDecrite} · ${etat} · compte ${qui}`)
  return { salon, prestations: parNom, employes, token: tokenPro, compte: qui }
}

bleu('→ Salons, équipes et catalogues')

const darZine = await installer({
  proprietaire: { identifiant: 'pro.darzine', prenom: 'Leila', nom: 'Amrani',
                  email: 'leila@darzine.ma' },
  fiche: {
    nom: 'Dar Zine', ville: 'Marrakech', quartier: 'Guéliz',
    adresse: '45 Rue Ibn Batouta', telephone: '0661234567',
    email: 'contact@darzine.ma', categorie: 'COIFFURE', delaiAnnulationHeures: 12,
    description: 'Coiffure et couleur au cœur du Guéliz, sur rendez-vous.',
  },
  prestations: [
    { nom: 'Coupe femme', categorie: 'Coupe', prix: 200, dureeMinutes: 45 },
    { nom: 'Brushing', categorie: 'Coiffage', prix: 120, dureeMinutes: 30 },
    { nom: 'Balayage', categorie: 'Couleur', prix: 650, dureeMinutes: 120 },
    { nom: 'Soin à l’huile d’argan', categorie: 'Soin', prix: 250, dureeMinutes: 45 },
  ],
  equipe: [
    // Sofia a un compte PRATICIEN : elle consulte son planning, rien de plus.
    { prenom: 'Sofia', nom: 'Alami', titre: 'Coloriste',
      fait: ['Coupe femme', 'Brushing', 'Balayage', 'Soin à l’huile d’argan'],
      compte: { identifiant: 'equipe.sofia', prenom: 'Sofia', nom: 'Alami',
                email: 'sofia@darzine.ma' } },
    // Youssef ne fait pas la couleur : le moteur ne doit jamais le proposer
    // sur le balayage.
    // Youssef est GESTIONNAIRE : Leila lui délègue la boutique. Il administre
    // tout sauf la suppression du salon, et sans connaître son mot de passe.
    { prenom: 'Youssef', nom: 'Tazi', titre: 'Coiffeur',
      fait: ['Coupe femme', 'Brushing'],
      role: 'GESTIONNAIRE',
      compte: { identifiant: 'equipe.youssef', prenom: 'Youssef', nom: 'Tazi',
                email: 'youssef@darzine.ma' } },
  ],
})

// Atlas Barber reste sur le compte pro1 (Karim Benali, barbier à Casablanca) :
// c'est cohérent, et cela laisse un salon accessible avec le compte de démo
// le plus connu.
const atlas = await installer({
  fiche: {
    nom: 'Atlas Barber', ville: 'Casablanca', quartier: 'Maarif',
    adresse: '8 Rue Al Massira', telephone: '0655443322',
    email: 'salam@atlasbarber.ma', categorie: 'BARBIER', delaiAnnulationHeures: 6,
    description: 'Coupe et barbe, sans attente.',
  },
  prestations: [
    { nom: 'Coupe homme', categorie: 'Coupe', prix: 80, dureeMinutes: 30 },
    { nom: 'Barbe', categorie: 'Barbe', prix: 50, dureeMinutes: 20 },
    { nom: 'Coupe + barbe', categorie: 'Forfait', prix: 120, dureeMinutes: 45 },
  ],
  equipe: [
    { prenom: 'Hamza', nom: 'Benjelloun', titre: 'Barbier',
      fait: ['Coupe homme', 'Barbe', 'Coupe + barbe'] },
    { prenom: 'Reda', nom: 'Chraibi', titre: 'Barbier',
      fait: ['Coupe homme', 'Barbe', 'Coupe + barbe'] },
  ],
})

const nails = await installer({
  proprietaire: { identifiant: 'pro.nails', prenom: 'Salma', nom: 'Berrada',
                  email: 'salma@nailsandco.ma' },
  fiche: {
    nom: 'Nails & Co', ville: 'Casablanca', quartier: 'Gauthier',
    adresse: '12 Rue Jean Jaurès', telephone: '0677889900',
    email: 'hello@nailsandco.ma', categorie: 'ONGLERIE', delaiAnnulationHeures: 24,
  },
  prestations: [
    { nom: 'Manucure simple', categorie: 'Mains', prix: 100, dureeMinutes: 30 },
    { nom: 'Semi-permanent', categorie: 'Mains', prix: 180, dureeMinutes: 60 },
    { nom: 'Pédicure', categorie: 'Pieds', prix: 150, dureeMinutes: 45 },
    { nom: 'Henné mains', categorie: 'Henné', prix: 150, dureeMinutes: 45 },
  ],
  equipe: [
    { prenom: 'Imane', nom: 'Ouazzani', titre: 'Prothésiste ongulaire',
      fait: ['Manucure simple', 'Semi-permanent', 'Pédicure', 'Henné mains'] },
  ],
})

const firdaws = await installer({
  proprietaire: { identifiant: 'pro.firdaws', prenom: 'Rachid', nom: 'Tazi',
                  email: 'rachid@alfirdaws.ma' },
  fiche: {
    nom: 'Hammam Al Firdaws', ville: 'Rabat', quartier: 'Agdal',
    adresse: '3 Avenue Fal Ould Oumeir', telephone: '0537778899',
    email: 'contact@alfirdaws.ma', categorie: 'SPA', delaiAnnulationHeures: 48,
  },
  prestations: [
    // Le vocabulaire d'un vrai hammam : c'est ce qu'on y demande, et ce qui
    // rend la démonstration crédible devant un gérant du métier.
    { nom: 'Hammam beldi', categorie: 'Hammam', prix: 200, dureeMinutes: 60 },
    { nom: 'Gommage au savon noir et gant kessa', categorie: 'Hammam', prix: 150, dureeMinutes: 40 },
    { nom: 'Enveloppement au rhassoul', categorie: 'Hammam', prix: 200, dureeMinutes: 45 },
    { nom: 'Massage à l’huile d’argan', categorie: 'Massage', prix: 400, dureeMinutes: 60 },
  ],
  equipe: [
    { prenom: 'Khadija', nom: 'Bennis', titre: 'Praticienne',
      fait: ['Hammam beldi', 'Gommage au savon noir et gant kessa', 'Enveloppement au rhassoul', 'Massage à l’huile d’argan'] },
  ],
})

const anfa = await installer({
  valider: false,
  proprietaire: { identifiant: 'pro.anfa', prenom: 'Nadia', nom: 'Filali',
                  email: 'nadia@salonanfa.ma' },
  fiche: {
    nom: 'Salon Anfa', ville: 'Casablanca', quartier: 'Anfa',
    adresse: "60 Boulevard d'Anfa", telephone: '0522334455',
    email: 'contact@salonanfa.ma', categorie: 'ESTHETIQUE', delaiAnnulationHeures: 24,
  },
  prestations: [{ nom: 'Soin du visage', categorie: 'Soin', prix: 350, dureeMinutes: 60 }],
  equipe: [{ prenom: 'Nadia', nom: 'Filali', titre: 'Esthéticienne', fait: ['Soin du visage'] }],
})

/* ------------------------------------------------------------------ */

bleu('→ Historique du salon')

/** Rendez-vous passé, saisi par le salon comme s'il venait du téléphone. */
const passe = async (lieu, prestation, employe, joursAvant, heure, clientNom, clientTelephone) => {
  const d = new Date()
  d.setDate(d.getDate() - joursAvant)
  const [h, m] = heure.split(':').map(Number)
  d.setHours(h, m, 0, 0)

  const r = await appel('POST', `/api/pro/salons/${lieu.salon.id}/reservations`, {
    prestationId: lieu.prestations[prestation].id,
    employeId: lieu.employes[employe].id,
    debut: d.toISOString(),
    clientNom, clientTelephone, origine: 'TELEPHONE',
  }, lieu.token)

  await appel('PATCH', `/api/pro/reservations/${r.id}/statut?statut=HONOREE`, null, lieu.token)
  return r
}

await passe(darZine, 'Coupe femme', 0, 9, '10:00', 'Mme Bennani', '0670112233')
await passe(darZine, 'Balayage', 0, 16, '14:00', 'Mme Alaoui', '0661445566')
await passe(atlas, 'Coupe + barbe', 0, 4, '17:00', 'M. Sbai', '0699001122')
await passe(atlas, 'Coupe homme', 1, 11, '11:00', 'M. Idrissi', '0688223344')
vert('4 rendez-vous passés, marqués honorés — de quoi remplir l\'agenda et les statistiques')

/* ------------------------------------------------------------------ */

bleu('→ Réservations du compte client1')

/** Réserve le premier jour disponible, au milieu de la journée. */
const reserver = async (lieu, prestation) => {
  const p = lieu.prestations[prestation]
  const jours = await (await fetch(
    `${API}/api/public/salons/${lieu.salon.id}/prochaines-dispos?prestationId=${p.id}&jours=14`)).json()
  if (jours.length === 0) throw new Error(`aucune disponibilité pour ${prestation}`)

  const dispos = await (await fetch(
    `${API}/api/public/salons/${lieu.salon.id}/disponibilites?prestationId=${p.id}&date=${jours[0]}`)).json()
  const creneau = dispos.creneaux[Math.floor(dispos.creneaux.length / 2)]

  return appel('POST', '/api/reservations', {
    salonId: lieu.salon.id, prestationId: p.id, debut: creneau.debut,
  }, t[CLIENT])
}

const r1 = await reserver(darZine, 'Coupe femme')
const r2 = await reserver(atlas, 'Coupe + barbe')
const r3 = await reserver(nails, 'Semi-permanent')
vert('3 réservations à venir — visibles sur /compte, annulables selon le préavis')

/* ------------------------------------------------------------------ */

bleu('→ Avis')

/**
 * Un avis exige un rendez-vous HONOREE : le salon marque le passage, puis le
 * client note. C'est ce chaînage qui rend les faux avis impossibles.
 */
const avis = async (lieu, reservation, note, commentaire) => {
  // C'est le salon qui atteste du passage, avec SON compte : un autre
  // professionnel n'a aucun droit sur ce rendez-vous.
  await appel('PATCH', `/api/pro/reservations/${reservation.id}/statut?statut=HONOREE`,
    null, lieu.token)
  return appel('POST', '/api/avis', { reservationId: reservation.id, note, commentaire }, t[CLIENT])
}

const a1 = await avis(darZine, r1, 5,
  'Sofia a parfaitement compris ce que je voulais. Salon impeccable.')
await avis(atlas, r2, 4, 'Bonne coupe, un peu d\'attente à l\'arrivée.')
await appel('POST', `/api/pro/avis/${a1.id}/reponse`,
  { reponse: 'Merci beaucoup ! Au plaisir de vous revoir.' }, darZine.token)
vert('2 avis, dont un avec réponse du salon')

// r3 reste CONFIRMEE : c'est celle qui sert à tester l'annulation.
vert(`réservation #${r3.id} laissée à venir, pour essayer l'annulation`)

/* ------------------------------------------------------------------ */

bleu('→ Récapitulatif')
const publics = await (await fetch(`${API}/api/public/salons?size=20`)).json()
console.log(`  ${publics.totalElements} salons visibles publiquement :`)
for (const s of publics.content) {
  const note = s.noteMoyenne ? `${s.noteMoyenne}/5 (${s.nombreAvis} avis)` : 'pas encore noté'
  console.log(`    · ${s.nom.padEnd(20)} ${s.ville.padEnd(12)} ${note}`)
}
const attente = await appel('GET', '/api/admin/salons?statut=EN_ATTENTE', null, t[ADMIN])
console.log(`  ${attente.totalElements} salon en attente : ${attente.content.map((s) => s.nom).join(', ')}`)

console.log()
// Les adresses suivent l'environnement : le script sert au développement,
// où chaque service a son port, comme à la pile partagée, où tout tient
// derrière une seule adresse. Les afficher en dur envoyait la personne qui
// vient d'installer la pile vers des ports qui n'écoutent rien chez elle.
const SITE = process.env.BASE_URL ?? 'http://localhost:5173'
const COURRIER = process.env.MAILPIT_URL ?? 'http://localhost:8025'
console.log(`  \x1b[36mInterface\x1b[0m   ${SITE}`)
console.log(`  \x1b[36mEmails\x1b[0m      ${COURRIER}`)
console.log(`  \x1b[36mAPI\x1b[0m         ${API}/swagger-ui.html`)
console.log()
console.log('  \x1b[36mComptes\x1b[0m — mot de passe identique à l\'identifiant')
console.log('    client1                    réserver, noter, annuler')
console.log('    admin                      valider les salons')
for (const lieu of [atlas, darZine, nails, firdaws, anfa]) {
  console.log(`    ${lieu.compte.padEnd(26)} ${lieu.salon.nom}`)
}
console.log()
console.log('  \x1b[36mMembres d\'équipe de Dar Zine\x1b[0m — même convention de mot de passe')
console.log('    equipe.sofia               PRATICIEN    · son planning seulement')
console.log('    equipe.youssef             GESTIONNAIRE · administre le salon, sans le supprimer')
console.log()
console.log('  Chaque salon a son propre propriétaire, et Dar Zine illustre la délégation :')
console.log('  Youssef gère la boutique sans connaître le mot de passe de Leila.')
