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
const installer = async ({ fiche, prestations, equipe, valider = true }) => {
  const salon = await appel('POST', '/api/salons', fiche, t[PRO])

  const parNom = {}
  for (const p of prestations) {
    parNom[p.nom] = await appel('POST', `/api/prestations/salon/${salon.id}`, p, t[PRO])
  }

  const employes = []
  for (const membre of equipe) {
    const { fait, ...profil } = membre
    const e = await appel('POST', `/api/pro/salons/${salon.id}/employes`, profil, t[PRO])
    await appel('PUT', `/api/pro/employes/${e.id}/prestations`,
      { prestationIds: fait.map((nom) => parNom[nom].id) }, t[PRO])
    employes.push(e)
  }

  await appel('PUT', `/api/pro/salons/${salon.id}/horaires`, SEMAINE, t[PRO])
  if (valider) {
    await appel('PATCH', `/api/admin/salons/${salon.id}/statut?statut=ACTIF`, null, t[ADMIN])
  }

  const etat = valider ? 'en ligne' : 'EN ATTENTE de validation'
  vert(`${fiche.nom} — ${fiche.ville} · ${prestations.length} prestations · `
     + `${employes.map((e) => e.prenom).join(', ')} · ${etat}`)
  return { salon, prestations: parNom, employes }
}

bleu('→ Salons, équipes et catalogues')

const darZine = await installer({
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
  ],
  equipe: [
    { prenom: 'Sofia', nom: 'Alami', titre: 'Coloriste',
      fait: ['Coupe femme', 'Brushing', 'Balayage'] },
    // Youssef ne fait pas la couleur : le moteur ne doit jamais le proposer
    // sur le balayage.
    { prenom: 'Youssef', nom: 'Tazi', titre: 'Coiffeur',
      fait: ['Coupe femme', 'Brushing'] },
  ],
})

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
  fiche: {
    nom: 'Nails & Co', ville: 'Casablanca', quartier: 'Gauthier',
    adresse: '12 Rue Jean Jaurès', telephone: '0677889900',
    email: 'hello@nailsandco.ma', categorie: 'ONGLERIE', delaiAnnulationHeures: 24,
  },
  prestations: [
    { nom: 'Manucure simple', categorie: 'Mains', prix: 100, dureeMinutes: 30 },
    { nom: 'Semi-permanent', categorie: 'Mains', prix: 180, dureeMinutes: 60 },
    { nom: 'Pédicure', categorie: 'Pieds', prix: 150, dureeMinutes: 45 },
  ],
  equipe: [
    { prenom: 'Imane', nom: 'Ouazzani', titre: 'Prothésiste ongulaire',
      fait: ['Manucure simple', 'Semi-permanent', 'Pédicure'] },
  ],
})

await installer({
  fiche: {
    nom: 'Hammam Al Firdaws', ville: 'Rabat', quartier: 'Agdal',
    adresse: '3 Avenue Fal Ould Oumeir', telephone: '0537778899',
    email: 'contact@alfirdaws.ma', categorie: 'SPA', delaiAnnulationHeures: 48,
  },
  prestations: [
    { nom: 'Hammam traditionnel', categorie: 'Hammam', prix: 200, dureeMinutes: 60 },
    { nom: 'Massage relaxant', categorie: 'Massage', prix: 400, dureeMinutes: 60 },
  ],
  equipe: [
    { prenom: 'Khadija', nom: 'Bennis', titre: 'Praticienne',
      fait: ['Hammam traditionnel', 'Massage relaxant'] },
  ],
})

await installer({
  valider: false,
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
  }, t[PRO])

  await appel('PATCH', `/api/pro/reservations/${r.id}/statut?statut=HONOREE`, null, t[PRO])
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
const avis = async (reservation, note, commentaire) => {
  await appel('PATCH', `/api/pro/reservations/${reservation.id}/statut?statut=HONOREE`, null, t[PRO])
  return appel('POST', '/api/avis', { reservationId: reservation.id, note, commentaire }, t[CLIENT])
}

const a1 = await avis(r1, 5, 'Sofia a parfaitement compris ce que je voulais. Salon impeccable.')
await avis(r2, 4, 'Bonne coupe, un peu d\'attente à l\'arrivée.')
await appel('POST', `/api/pro/avis/${a1.id}/reponse`,
  { reponse: 'Merci beaucoup ! Au plaisir de vous revoir.' }, t[PRO])
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
console.log('  \x1b[36mInterface\x1b[0m   http://localhost:5173')
console.log('  \x1b[36mEmails\x1b[0m      http://localhost:8025')
console.log('  \x1b[36mAPI\x1b[0m         http://localhost:8080/swagger-ui.html')
console.log()
console.log('  Comptes : client1 / pro1 / admin — mot de passe identique à l\'identifiant')
