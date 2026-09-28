/*
 * Le salon, sur le panneau de connexion.
 *
 * Ce panneau est le premier écran que voit un gérant à qui l'on vient de
 * confier ses accès. Il doit dire ce que fait le produit, pas remplir une
 * moitié d'écran.
 *
 * Il portait jusqu'ici un ciel dégradé traversé par un astre. L'intention
 * était bonne — l'heure du Maroc, montrée plutôt qu'écrite — mais vu petit,
 * l'astre se lisait comme une tache grise sur l'écran, et sur téléphone il
 * passait derrière le texte. Surtout, un ciel ne dit rien d'un salon de
 * beauté : c'était joli et muet.
 *
 * À la place, une scène : un miroir de salon avec ses ampoules, un comptoir,
 * ses flacons — et, posée devant, une carte de créneaux. Le métier à gauche,
 * le produit au milieu. C'est la phrase du site, dessinée.
 *
 * Deux choses seulement y bougent, et toutes deux disent l'heure de
 * Casablanca : le reflet du miroir, abricot en plein jour et prune le soir,
 * et les ampoules, qui s'allument à la nuit tombée. Un vrai salon fait
 * exactement cela.
 *
 * Tout est facultatif. Sans ce script, le panneau garde son fond, sa marque
 * et son accroche ; aucune information essentielle n'y transite.
 */
(function () {
  "use strict";

  /*
   * Attendre le document.
   *
   * Keycloak place ses scripts dans le <head>, sans `defer` : au moment où
   * celui-ci s'exécute, le panneau n'existe pas encore. Une première version
   * de l'ancien script cherchait `.b-header`, ne le trouvait pas, et sortait
   * en silence — aucune erreur, simplement rien à l'écran, ce qui est le pire
   * des deux.
   */
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", demarrer);
  } else {
    demarrer();
  }

  var ZONE = "Africa/Casablanca";

  /*
   * La scène, en un seul SVG.
   *
   * Aucune coordonnée n'est laissée au hasard : le repère fait 420 × 300, le
   * comptoir est à y=240, et tout ce qui se pose dessus y descend exactement.
   * Une pièce qui flotte d'un pixel au-dessus de son support se voit.
   *
   * Les deux dégradés du reflet sont pilotés par des classes et non par des
   * attributs : Safari a longtemps ignoré `var()` dans un attribut de
   * présentation, et le miroir y serait resté noir.
   */
  var DESSIN = [
    '<svg viewBox="0 0 420 300" xmlns="http://www.w3.org/2000/svg" role="presentation" focusable="false">',
    '<defs>',
    /* Le reflet : clair en haut, plus soutenu vers le bas, comme une vitre
       qui prend la lumière de la pièce. */
    '<linearGradient id="b-glace" x1="0" y1="0" x2="0.3" y2="1">',
    '<stop class="b-glace-haut" offset="0"/>',
    '<stop class="b-glace-bas" offset="1"/>',
    '</linearGradient>',
    /* La lueur des murs, qui détache le miroir du fond. */
    '<radialGradient id="b-lueur">',
    '<stop offset="0" stop-color="#fffaf7" stop-opacity="0.15"/>',
    '<stop offset="1" stop-color="#fffaf7" stop-opacity="0"/>',
    '</radialGradient>',
    /* Le halo d'une ampoule. Plus serré que la lueur des murs : une ampoule
       éclaire fort et près. */
    '<radialGradient id="b-halo">',
    '<stop offset="0" stop-color="#ffe9d6" stop-opacity="0.55"/>',
    '<stop offset="0.55" stop-color="#ffcfae" stop-opacity="0.18"/>',
    '<stop offset="1" stop-color="#ffcfae" stop-opacity="0"/>',
    '</radialGradient>',
    '<clipPath id="b-clip-glace">',
    '<rect x="128" y="62" width="164" height="164" rx="10"/>',
    '</clipPath>',
    '<filter id="b-ombre" x="-40%" y="-40%" width="180%" height="180%">',
    '<feDropShadow dx="0" dy="8" stdDeviation="12" flood-color="#160814" flood-opacity="0.5"/>',
    '</filter>',
    '</defs>',

    /* ---- Le décor, qui suit le curseur de loin ---- */
    '<g class="b-plan-fond">',
    '<ellipse cx="210" cy="132" rx="190" ry="152" fill="url(#b-lueur)"/>',

    /* ---- Le miroir ---- */
    '<rect x="118" y="24" width="184" height="212" rx="16" fill="#fffaf7" fill-opacity="0.07"/>',
    '<rect x="119" y="25" width="182" height="210" rx="15" fill="none" stroke="#fffaf7" stroke-opacity="0.22" stroke-width="2"/>',
    '<g clip-path="url(#b-clip-glace)">',
    '<rect x="128" y="62" width="164" height="164" fill="url(#b-glace)"/>',
    /* Deux bandes obliques : c'est ce qui fait lire « vitre » plutôt que
       « rectangle coloré ». */
    '<path d="M132 226 L206 62 L236 62 L162 226 Z" fill="#fffaf7" fill-opacity="0.13"/>',
    '<path d="M186 226 L260 62 L272 62 L198 226 Z" fill="#fffaf7" fill-opacity="0.07"/>',
    '</g>',

    /* ---- Les ampoules, en bandeau sous le haut du cadre ---- */
    '<g class="b-ampoules">',
    ampoules([150, 190, 230, 270], 44),
    '</g>',

    /* ---- Le comptoir ---- */
    '<rect x="34" y="240" width="352" height="9" rx="4.5" fill="#fffaf7" fill-opacity="0.26"/>',
    '<rect x="34" y="249" width="352" height="6" rx="3" fill="#fffaf7" fill-opacity="0.09"/>',

    /* ---- Ce qui est posé dessus, à gauche ---- */
    /* Un vase et deux brins : la seule touche organique de la scène, et la
       seule à porter l'abricot de la palette. */
    '<rect x="56" y="212" width="24" height="28" rx="7" fill="#fffaf7" fill-opacity="0.24"/>',
    '<path d="M68 212 C 67 198, 62 190, 57 184" stroke="#ffbb94" stroke-opacity="0.85" stroke-width="2" stroke-linecap="round" fill="none"/>',
    '<path d="M68 212 C 70 200, 75 193, 80 189" stroke="#ffbb94" stroke-opacity="0.6" stroke-width="2" stroke-linecap="round" fill="none"/>',
    /* Des feuilles, et non des ellipses inclinées : une ellipse posée au bout
       d'une tige se lit comme un champignon. Une pointe et un creux suffisent
       à faire une feuille. */
    '<path d="M57 184 C 50 180, 46 171, 49 165 C 56 169, 60 178, 57 184 Z" fill="#ffbb94" fill-opacity="0.82"/>',
    '<path d="M80 189 C 86 186, 90 179, 88 174 C 82 177, 78 184, 80 189 Z" fill="#ffbb94" fill-opacity="0.58"/>',
    /* Un flacon haut et un pot bas : deux hauteurs, sinon la rangée est plate. */
    '<rect x="92" y="198" width="18" height="42" rx="5" fill="#fffaf7" fill-opacity="0.2"/>',
    '<rect x="98" y="189" width="6" height="10" fill="#fffaf7" fill-opacity="0.2"/>',
    '<rect x="95" y="183" width="12" height="7" rx="2.5" fill="#ffbb94" fill-opacity="0.75"/>',
    '<rect x="118" y="219" width="24" height="21" rx="6" fill="#fffaf7" fill-opacity="0.22"/>',
    '<rect x="121" y="214" width="18" height="6" rx="3" fill="#fffaf7" fill-opacity="0.38"/>',
    '</g>',

    /* ---- La carte de rendez-vous, posée sur le comptoir ---- */
    /*
     * Elle chevauche le miroir : c'est ce recouvrement qui donne la
     * profondeur, et qui met la réservation au premier plan — devant le
     * décor, ce qui est exactement l'ordre d'importance.
     *
     * Les heures sont écrites, pas suggérées par des barres : c'est le seul
     * endroit du dessin où le produit se nomme, et « 09:00 » se lit dans les
     * trois langues du thème.
     */
    '<g class="b-plan-carte">',
    '<g filter="url(#b-ombre)">',
    '<rect x="232" y="148" width="152" height="92" rx="13" fill="#fffaf7"/>',
    '</g>',
    '<rect x="245" y="162" width="68" height="6" rx="3" fill="#4c1d3d" fill-opacity="0.22"/>',
    creneaux(),
    '</g>',
    '</svg>',
  ].join("");

  /** Les ampoules : un halo large, puis le verre. */
  function ampoules(xs, y) {
    return xs.map(function (x) {
      return '<circle cx="' + x + '" cy="' + y + '" r="17" fill="url(#b-halo)"/>'
           + '<circle cx="' + x + '" cy="' + y + '" r="6.5" fill="#fffaf7" fill-opacity="0.92"/>';
    }).join("");
  }

  /**
   * Six créneaux, dont un retenu — et qu'on peut changer.
   *
   * C'est le seul endroit du dessin où l'on peut toucher quelque chose, et
   * c'est voulu : survoler un créneau le retient, exactement comme sur la
   * fiche d'un salon. L'écran de connexion fait alors ce que fait le produit,
   * au lieu de le raconter.
   *
   * Le créneau retenu porte la framboise pleine — la couleur du bouton « Se
   * connecter » qui lui fait face de l'autre côté de l'écran. Les deux se
   * répondent : c'est le même geste, choisir.
   *
   * Les couleurs sont posées par la feuille de style et non par des attributs,
   * pour qu'elles puissent se transitionner. Un attribut `fill` ne s'anime
   * pas ; une propriété CSS, oui.
   *
   * Aucun de ces créneaux n'est atteignable au clavier, et c'est assumé : le
   * dessin est décoratif, il porte `aria-hidden`, et ne rien y toucher ne fait
   * rien perdre. Le rendre focalisable ajouterait six arrêts de tabulation
   * avant le champ « identifiant », pour un jouet.
   */
  function creneaux() {
    var heures = [["09:00", "09:30", "10:00"], ["10:30", "11:00", "11:30"]];
    var retenu = "10:30";
    var out = "";
    heures.forEach(function (rangee, r) {
      var y = 178 + r * 26;
      rangee.forEach(function (h, c) {
        var x = 245 + c * 44;
        var pris = h === retenu ? " b-creneau--pris" : "";
        out += '<g class="b-creneau' + pris + '" data-heure="' + h + '">'
             + '<rect x="' + x + '" y="' + y + '" width="38" height="19" rx="9.5"/>'
             + '<text x="' + (x + 19) + '" y="' + (y + 13.5) + '" text-anchor="middle"'
             + ' font-family="Inter, -apple-system, Segoe UI, Roboto, sans-serif"'
             + ' font-size="10" font-weight="600">' + h + "</text>"
             + "</g>";
      });
    });
    return out;
  }

  /**
   * Le créneau suit le curseur.
   *
   * Sur écran tactile, le survol n'existe pas : le `click` sert alors de
   * repli, et la même règle s'applique aux deux.
   */
  function rendreChoisissable(scene) {
    var creneaux = scene.querySelectorAll(".b-creneau");
    function retenir(cible) {
      for (var i = 0; i < creneaux.length; i++) {
        creneaux[i].classList.toggle("b-creneau--pris", creneaux[i] === cible);
      }
    }
    for (var i = 0; i < creneaux.length; i++) {
      (function (g) {
        g.addEventListener("mouseenter", function () { retenir(g); });
        g.addEventListener("click", function () { retenir(g); });
      })(creneaux[i]);
    }
  }

  /**
   * Sur l'écran d'inscription, le créneau se prend sous les yeux du visiteur.
   *
   * La carte arrive avec ses six heures libres, et l'une d'elles se retient
   * après une seconde. C'est exactement ce que la personne est en train de
   * faire — ouvrir un compte pour prendre un premier rendez-vous — montré au
   * moment où elle hésite encore à remplir le formulaire.
   *
   * Sur l'écran de connexion, le créneau est déjà pris : on y revient, on a
   * déjà ses habitudes.
   *
   * Pour qui a demandé que rien ne bouge, le créneau est simplement là dès le
   * départ : l'image reste juste, seule l'animation disparaît.
   */
  function animerPremierCreneau(scene) {
    if (!document.getElementById("kc-register-form")) return;

    var retenu = scene.querySelector(".b-creneau--pris");
    if (!retenu) return;

    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    retenu.classList.remove("b-creneau--pris");
    setTimeout(function () {
      /* Rien si le visiteur a déjà choisi lui-même entre-temps : on ne
         reprend pas la main sur un geste qu'il vient de faire. */
      if (!scene.querySelector(".b-creneau--pris")) {
        retenu.classList.add("b-creneau--pris");
      }
    }, 900);
  }

  /**
   * La profondeur : la carte flotte davantage que le décor.
   *
   * Deux plans et deux amplitudes suffisent à détacher la carte du miroir —
   * c'est la seule chose qui fasse lire « posée devant » plutôt que
   * « collée dessus ».
   *
   * Rien de tout cela sur écran tactile ni pour qui a demandé que rien ne
   * bouge : un pointeur grossier n'a pas de survol, et l'effet n'aurait pour
   * seul résultat que de faire sauter le dessin au moindre appui.
   */
  function rendreFlottante(panneau, scene) {
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    if (!window.matchMedia("(pointer: fine)").matches) return;

    var attendu = false;
    panneau.addEventListener("mousemove", function (e) {
      if (attendu) return;
      attendu = true;
      /* Une seule mise à jour par image : sans cela, un mouvement rapide
         déclenche des dizaines de calculs de style pour un déplacement que
         personne ne perçoit. */
      requestAnimationFrame(function () {
        attendu = false;
        var r = panneau.getBoundingClientRect();
        var dx = (e.clientX - r.left) / r.width - 0.5;
        var dy = (e.clientY - r.top) / r.height - 0.5;
        /* En unités du repère du dessin, pas en pixels : l'effet suit alors
           l'échelle de la scène, et reste le même sur un écran de portable
           et sur un grand moniteur. */
        scene.style.setProperty("--px", (dx * 16).toFixed(1) + "px");
        scene.style.setProperty("--py", (dy * 16).toFixed(1) + "px");
      });
    });
    panneau.addEventListener("mouseleave", function () {
      scene.style.setProperty("--px", "0px");
      scene.style.setProperty("--py", "0px");
    });
  }

  /* ------------------------------------------------------------------ */
  /* L'heure de Casablanca                                              */
  /* ------------------------------------------------------------------ */

  /** L'heure et la minute là-bas, quelle que soit l'heure d'ici. */
  function maintenantAuMaroc() {
    var parts = new Intl.DateTimeFormat("fr-FR", {
      timeZone: ZONE, hour: "2-digit", minute: "2-digit", hour12: false,
    }).formatToParts(new Date());
    var lu = function (type) {
      var p = parts.filter(function (x) { return x.type === type; })[0];
      return p ? parseInt(p.value, 10) : 0;
    };
    /* `hour: "2-digit"` rend « 24 » à minuit en français, jamais « 00 ». */
    return { h: lu("hour") % 24, m: lu("minute") };
  }

  /**
   * La part de nuit, de 0 à 1.
   *
   * Le passage s'étale sur l'aube et le crépuscule plutôt que de basculer à
   * heure fixe : un changement net à 19 h 00 précises se verrait comme un
   * défaut d'affichage, pas comme un coucher de soleil.
   */
  function partDeNuit(h, m) {
    var t = h + m / 60;
    if (t >= 8 && t < 18) return 0;
    if (t >= 6 && t < 8) return 1 - (t - 6) / 2;   /* l'aube : la nuit s'efface */
    if (t >= 18 && t < 21) return (t - 18) / 3;    /* le crépuscule : elle revient */
    return 1;
  }

  /** Mélange deux couleurs #rrggbb. */
  function melange(a, b, k) {
    var lire = function (c) {
      return [parseInt(c.slice(1, 3), 16), parseInt(c.slice(3, 5), 16), parseInt(c.slice(5, 7), 16)];
    };
    var x = lire(a), y = lire(b);
    return "rgb(" + x.map(function (v, i) {
      return Math.round(v + (y[i] - v) * k);
    }).join(",") + ")";
  }

  var JOUR = { haut: "#ffcfae", bas: "#fb9590" };
  var NUIT = { haut: "#7d3050", bas: "#38152e" };

  /* La phrase sous l'accroche. Trois états, parce que trois situations
     différentes pour qui lit : le salon est ouvert, il va ouvrir, il dort. */
  var PHRASES = {
    fr: {
      ouvert: "Il est <strong>{H}</strong> à Casablanca. Les salons prennent vos rendez-vous.",
      bientot: "Il est <strong>{H}</strong> à Casablanca. Les salons ouvrent bientôt.",
      ferme: "Il est <strong>{H}</strong> à Casablanca. Les salons dorment, leur agenda non.",
    },
    en: {
      ouvert: "It is <strong>{H}</strong> in Casablanca. Salons are taking bookings.",
      bientot: "It is <strong>{H}</strong> in Casablanca. Salons open shortly.",
      ferme: "It is <strong>{H}</strong> in Casablanca. The salons sleep, their diary does not.",
    },
    ar: {
      ouvert: "الساعة <strong>{H}</strong> بالدار البيضاء. الصالونات تستقبل حجوزاتكم.",
      bientot: "الساعة <strong>{H}</strong> بالدار البيضاء. الصالونات تفتح قريبا.",
      ferme: "الساعة <strong>{H}</strong> بالدار البيضاء. الصالونات نائمة، لكن مواعيدها لا.",
    },
  };

  function demarrer() {
    var panneau = document.querySelector(".b-header");
    if (!panneau) return;

    /* La scène est insérée en tête : la marque reste en bas du panneau, que
       `justify-content: space-between` sépare. */
    var scene = document.createElement("div");
    scene.className = "b-scene";
    scene.setAttribute("aria-hidden", "true");
    scene.innerHTML = DESSIN;
    panneau.insertBefore(scene, panneau.firstChild);

    rendreChoisissable(scene);
    animerPremierCreneau(scene);
    rendreFlottante(panneau, scene);

    /* La ligne d'heure, sous l'accroche. Ajoutée ici et non dans le gabarit :
       `base` ne prévoit aucun emplacement pour du texte libre, et reprendre
       template.ftl pour une ligne obligerait à suivre chaque montée de
       version de Keycloak. */
    var marque = panneau.querySelector(".b-brand");
    if (marque) {
      var ligne = document.createElement("p");
      ligne.className = "b-heure";
      marque.appendChild(ligne);
    }

    peindre(panneau);
    /* Toutes les minutes : la lumière change lentement, et une page de
       connexion reste rarement ouverte des heures. */
    setInterval(function () { peindre(panneau); }, 60000);
  }

  function peindre(panneau) {
    var t = maintenantAuMaroc();
    var nuit = partDeNuit(t.h, t.m);

    panneau.style.setProperty("--glace-haut", melange(JOUR.haut, NUIT.haut, nuit));
    panneau.style.setProperty("--glace-bas", melange(JOUR.bas, NUIT.bas, nuit));
    /* Les ampoules : discrètes en plein jour, franches la nuit. Jamais
       éteintes — un miroir de salon aux ampoules noires se lit comme un
       salon fermé, or l'agenda, lui, ne ferme pas. */
    panneau.style.setProperty("--ampoule-eclat", (0.45 + nuit * 0.55).toFixed(2));

    var ligne = panneau.querySelector(".b-heure");
    if (!ligne) return;
    var langue = (document.documentElement.lang || "fr").slice(0, 2);
    var jeu = PHRASES[langue] || PHRASES.fr;
    var etat = t.h >= 9 && t.h < 19 ? "ouvert" : (t.h >= 7 && t.h < 9 ? "bientot" : "ferme");
    var hhmm = ("0" + t.h).slice(-2) + ":" + ("0" + t.m).slice(-2);
    ligne.innerHTML = jeu[etat].replace("{H}", hhmm);
  }
})();
