/*
 * Le ciel de Casablanca, sur le panneau de connexion.
 *
 * Ce panneau est le premier écran que voit un gérant à qui l'on vient de
 * confier ses accès. Il devrait dire quelque chose du produit plutôt que de
 * remplir une moitié d'écran.
 *
 *   1. Les couleurs du ciel suivent l'heure réelle au Maroc.
 *   2. Un astre le traverse : soleil chaud le jour, lune pâle la nuit. Un
 *      disque et son halo, rien de plus. Il ne disparaît jamais — un panneau
 *      vide à 2 h du matin serait un écran cassé, pas une nuit.
 *   3. Un jardin en silhouette tient l'horizon, et glisse à contresens de
 *      l'astre quand la souris bouge : c'est ce décalage qui donne la
 *      profondeur, bien plus que le déplacement lui-même.
 *   4. Une ligne dit l'heure qu'il est là-bas, et ce qu'elle implique — le
 *      salon dort, son agenda non. C'est la promesse du produit, montrée au
 *      lieu d'être écrite.
 *
 * Tout est facultatif. Sans ce script, le panneau reste un dégradé chaud avec
 * son accroche : aucune information essentielle n'y transite.
 */
(function () {
  "use strict";

  /*
   * Attendre le document.
   *
   * Keycloak place ses scripts dans le <head>, sans `defer` : au moment où
   * celui-ci s'exécute, le panneau n'existe pas encore. La première version
   * cherchait `.b-header`, ne le trouvait pas, et sortait en silence — aucune
   * erreur dans la console, simplement rien à l'écran, ce qui est le pire des
   * deux.
   */
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", demarrer);
  } else {
    demarrer();
  }

  function demarrer() {
  var panneau = document.querySelector(".b-header");
  if (!panneau) return;

  var ZONE = "Africa/Casablanca";

  /*
   * Les moments du jour, et leur ciel. Interpolés entre eux, jamais commutés :
   * un basculement net à heure fixe se verrait comme un défaut d'affichage.
   *
   * `nuit` va de 0 à 1 et ne sert pas à faire disparaître l'astre mais à le
   * changer de nature : il refroidit, pâlit et rapetisse jusqu'à devenir une
   * lune. Le fondu s'étale sur l'aube et le crépuscule, si bien qu'on ne voit
   * pas le passage — seulement qu'il a eu lieu.
   *
   * La nuit est un vert profond et non un brun : c'est la teinte sombre de la
   * palette (celle des barbiers), et un ciel nocturne brun ressemble à un
   * écran mal calibré.
   */
  var MOMENTS = [
    { h: 0,  haut: "#1f382f", bas: "#102220", eclat: 0.88, nuit: 1.00 },
    /* Ce point tient le vert au milieu de la nuit. Sans lui, l'interpolation
       directe de minuit vers l'avant-jour passait par un gris ardoise : le
       panneau avait alors l'air éteint plutôt que nocturne. */
    { h: 3,  haut: "#20352f", bas: "#111f1e", eclat: 0.85, nuit: 1.00 },
    { h: 5,  haut: "#42323f", bas: "#201c25", eclat: 0.72, nuit: 1.00 },
    { h: 6,  haut: "#8c4a34", bas: "#43241a", eclat: 0.78, nuit: 0.55 },
    { h: 8,  haut: "#c67e52", bas: "#8a4a30", eclat: 0.94, nuit: 0.00 },
    { h: 13, haut: "#e0a468", bas: "#b0663c", eclat: 1.00, nuit: 0.00 },
    { h: 18, haut: "#c9724a", bas: "#7a3a24", eclat: 0.92, nuit: 0.00 },
    { h: 20, haut: "#6b3320", bas: "#3a1d13", eclat: 0.78, nuit: 0.40 },
    { h: 22, haut: "#38302c", bas: "#1c2320", eclat: 0.88, nuit: 1.00 },
    { h: 24, haut: "#1f382f", bas: "#102220", eclat: 0.88, nuit: 1.00 },
  ];

  /* Les deux visages de l'astre : un cœur et un halo, mélangés composante par
     composante selon `nuit`. Le halo est la même teinte que le cœur, plus
     chaude et beaucoup plus diluée — c'est lui qui fait le ciel autour. */
  var SOLEIL = {
    coeur: [255, 245, 230, 0.95],
    halo:  [255, 202, 140, 0.40],
  };
  var LUNE = {
    coeur: [242, 249, 245, 0.90],
    halo:  [190, 220, 208, 0.20],
  };

  function versRvb(hex) {
    return [1, 3, 5].map(function (i) { return parseInt(hex.substr(i, 2), 16); });
  }
  function versHex(c) {
    return "#" + c.map(function (v) {
      var s = Math.round(v).toString(16);
      return s.length === 1 ? "0" + s : s;
    }).join("");
  }
  function melanger(a, b, t) {
    var x = versRvb(a), y = versRvb(b);
    return versHex([0, 1, 2].map(function (i) { return x[i] + (y[i] - x[i]) * t; }));
  }
  function melangerRgba(a, b, t) {
    var c = [0, 1, 2].map(function (i) { return Math.round(a[i] + (b[i] - a[i]) * t); });
    return "rgba(" + c.join(",") + "," + (a[3] + (b[3] - a[3]) * t).toFixed(3) + ")";
  }

  /* Heure décimale à Casablanca, quel que soit le fuseau du visiteur — un
     gérant en déplacement doit voir l'heure de son salon, pas la sienne. */
  function heureLaBas() {
    var p = new Intl.DateTimeFormat("fr-FR", {
      timeZone: ZONE, hour: "2-digit", minute: "2-digit", hour12: false,
    }).formatToParts(new Date());
    var lire = function (type) {
      var e = p.find(function (x) { return x.type === type; });
      return e ? parseInt(e.value, 10) : 0;
    };
    return { h: lire("hour"), m: lire("minute") };
  }

  function etatDuCiel(decimal) {
    for (var i = 0; i < MOMENTS.length - 1; i++) {
      var a = MOMENTS[i], b = MOMENTS[i + 1];
      if (decimal >= a.h && decimal <= b.h) {
        var t = (decimal - a.h) / (b.h - a.h);
        return {
          haut: melanger(a.haut, b.haut, t),
          bas: melanger(a.bas, b.bas, t),
          eclat: a.eclat + (b.eclat - a.eclat) * t,
          nuit: a.nuit + (b.nuit - a.nuit) * t,
        };
      }
    }
    return MOMENTS[0];
  }

  /*
   * La course de l'astre. Deux arcs plutôt qu'un seul : le soleil monte de
   * 6 h à 20 h, la lune reprend le trajet de 20 h à 6 h, plus bas et plus
   * court. Aucun des deux ne passe sous l'horizon, et le ciel n'est donc
   * jamais vide.
   */
  function course(decimal) {
    var jour = decimal >= 6 && decimal < 20;
    var depuis = jour ? decimal - 6 : (decimal < 6 ? decimal + 24 : decimal) - 20;
    var avance = depuis / (jour ? 14 : 10);
    return {
      x: 16 + avance * 68,
      /* L'arc de nuit culmine plus bas : la lune ne prend pas la place du
         soleil, elle passe derrière. */
      y: (jour ? 92 : 84) - Math.sin(avance * Math.PI) * (jour ? 58 : 38),
    };
  }

  function peindre() {
    var t = heureLaBas();
    var decimal = t.h + t.m / 60;
    var ciel = etatDuCiel(decimal);
    var pos = course(decimal);

    panneau.style.setProperty("--ciel-haut", ciel.haut);
    panneau.style.setProperty("--ciel-bas", ciel.bas);
    panneau.style.setProperty("--astre-x", pos.x + "%");
    panneau.style.setProperty("--astre-y", pos.y + "%");
    panneau.style.setProperty("--astre-opacite", ciel.eclat.toFixed(2));
    /* La lune fait un peu plus du tiers du soleil : c'est sa taille, bien plus
       que sa couleur, qui la fait lire comme une lune. */
    panneau.style.setProperty("--astre-echelle", (1 - ciel.nuit * 0.58).toFixed(3));
    var halo = [0, 1, 2, 3].map(function (i) {
      return SOLEIL.halo[i] + (LUNE.halo[i] - SOLEIL.halo[i]) * ciel.nuit;
    });
    panneau.style.setProperty("--astre-coeur", melangerRgba(SOLEIL.coeur, LUNE.coeur, ciel.nuit));
    panneau.style.setProperty("--astre-halo", "rgba(" + halo.slice(0, 3).map(Math.round).join(",")
      + "," + halo[3].toFixed(3) + ")");
    /* Le bord doit être la teinte du halo à opacité nulle, et non `transparent`
       — qui vaut du noir transparent, et dessinait un liseré gris en fondu. */
    panneau.style.setProperty("--astre-bord", "rgba(" + halo.slice(0, 3).map(Math.round).join(",") + ",0)");
    /* Les bornes du dégradé. La nuit, le cœur s'élargit et le halo s'arrête
       plus tôt : la lune a un bord, le soleil de midi n'en a pas. */
    panneau.style.setProperty("--astre-net", (12 + ciel.nuit * 14).toFixed(1) + "%");
    panneau.style.setProperty("--astre-diffus", (27 + ciel.nuit * 9).toFixed(1) + "%");
    panneau.style.setProperty("--astre-limite", (68 - ciel.nuit * 10).toFixed(1) + "%");

    ecrireHeure(t);
  }

  /* La phrase. Trois états, parce que trois situations différentes pour qui
     lit : le salon est ouvert, il va ouvrir, il dort. */
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

  function ecrireHeure(t) {
    var ligne = document.querySelector(".b-heure");
    if (!ligne) return;
    var langue = (document.documentElement.lang || "fr").slice(0, 2);
    var jeu = PHRASES[langue] || PHRASES.fr;
    var etat = t.h >= 9 && t.h < 19 ? "ouvert" : (t.h >= 7 && t.h < 9 ? "bientot" : "ferme");
    var hhmm = ("0" + t.h).slice(-2) + ":" + ("0" + t.m).slice(-2);
    ligne.innerHTML = jeu[etat].replace("{H}", hhmm);
  }

  /* ---- Le jardin ---- */

  /* Une corolle de `n` pétales. La même courbe que `Motifs.jsx` côté
     application : les fleurs d'ici et les motifs du site sont les mêmes. */
  function petales(n, rayon, largeur) {
    var d = "";
    for (var i = 0; i < n; i++) {
      d += '<path transform="rotate(' + (360 * i) / n + ')" d="M0 0 C '
        + largeur + " " + (-rayon * 0.42) + " " + largeur + " " + (-rayon * 0.72)
        + ' 0 ' + -rayon + " C " + -largeur + " " + (-rayon * 0.72) + " "
        + -largeur + " " + (-rayon * 0.42) + ' 0 0 Z"/>';
    }
    return d;
  }

  /*
   * Le jardin.
   *
   * Neuf fleurs de tailles et d'inclinaisons différentes, posées irrégulière-
   * ment : un rang régulier se lirait comme une frise, pas comme un jardin.
   * Dessiné en silhouette sombre, il assombrit le bas du panneau — là,
   * justement, où sont la marque et l'accroche, qui y gagnent en contraste.
   * Il passe devant l'astre : c'est ce qui donne un horizon derrière lequel
   * se coucher.
   */
  var JARDIN = [
    { x: 48,  y: 208, t: 0.44, n: 6, r:  12, d: -14 },
    { x: 152, y: 250, t: 0.30, n: 5, r: -18, d:  10 },
    { x: 268, y: 178, t: 0.56, n: 8, r:  24, d:  18 },
    { x: 392, y: 238, t: 0.34, n: 6, r:  -6, d: -12 },
    { x: 520, y: 196, t: 0.48, n: 5, r:  16, d:  16 },
    { x: 648, y: 256, t: 0.28, n: 6, r: -22, d:  -8 },
    { x: 764, y: 184, t: 0.60, n: 8, r:   8, d:  20 },
    { x: 890, y: 244, t: 0.36, n: 5, r: -14, d: -10 },
    { x: 966, y: 200, t: 0.42, n: 6, r:  20, d:  14 },
  ];

  var jardin = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  jardin.setAttribute("class", "b-jardin");
  jardin.setAttribute("viewBox", "0 0 1000 300");
  /*
   * `meet`, et ancré en bas.
   *
   * `slice` remplissait bien le cadre mais rognait : le panneau étant bien
   * plus haut que large par rapport au dessin, il n'en gardait que la moitié
   * centrale, agrandie — trois fleurs énormes au lieu de neuf. `meet` les
   * garde toutes, à la largeur du panneau, posées sur son bord inférieur.
   * Une déformation non uniforme (`none`) écraserait les corolles.
   */
  jardin.setAttribute("preserveAspectRatio", "xMidYMax meet");
  jardin.setAttribute("aria-hidden", "true");
  jardin.innerHTML = JARDIN.map(function (f) {
    return '<path d="M' + f.x + " " + f.y + " C " + (f.x + f.d) + " " + (f.y + 40)
      + " " + (f.x - f.d) + " " + (f.y + 70) + " " + (f.x + f.d / 2) + ' 302" '
      + 'stroke-width="' + (4 + f.t * 6).toFixed(1) + '" fill="none"/>'
      + '<g transform="translate(' + f.x + " " + f.y + ") rotate(" + f.r
      + ") scale(" + f.t + ')">' + petales(f.n, 100, 26)
      + '<circle r="17"/></g>';
  }).join("");
  panneau.insertBefore(jardin, panneau.firstChild);

  /* Un simple élément vide : sa forme entière — le disque et le halo qui s'en
     échappe — tient dans un dégradé radial, que le CSS dessine mieux qu'un
     SVG et sans une seule coordonnée à tenir à jour. */
  var astre = document.createElement("div");
  astre.className = "b-astre";
  astre.setAttribute("aria-hidden", "true");
  /* Placé en tête, donc peint avant le jardin — qui le recouvre. Le soleil
     couchant doit passer derrière les fleurs et non devant : c'est ce seul
     détail qui fait la différence entre un horizon et un collage. */
  panneau.insertBefore(astre, panneau.firstChild);

  /* La ligne d'heure, sous l'accroche. Ajoutée ici et non dans le gabarit :
     `base` ne prévoit aucun emplacement pour du texte libre, et reprendre
     template.ftl pour une ligne obligerait à suivre chaque montée de version
     de Keycloak. */
  var marque = panneau.querySelector(".b-brand");
  if (marque) {
    var ligne = document.createElement("p");
    ligne.className = "b-heure";
    marque.appendChild(ligne);
  }

  peindre();
  /* Toutes les minutes : le ciel bouge lentement, et une page de connexion
     reste rarement ouverte des heures. */
  setInterval(peindre, 60000);

  /* ---- La parallaxe ---- */
  var doux = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var finPointeur = window.matchMedia("(pointer: fine)").matches;
  if (doux || !finPointeur) return;

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
      /* 26 px d'amplitude : assez pour que l'astre paraisse flotter devant le
         ciel, trop peu pour qu'on le prenne pour un élément à cliquer. */
      panneau.style.setProperty("--parallaxe-x", (dx * 26).toFixed(1) + "px");
      panneau.style.setProperty("--parallaxe-y", (dy * 26).toFixed(1) + "px");
      /* Le jardin part à contresens, et de moitié moins : c'est l'écart entre
         les deux plans qui donne la profondeur, pas leur déplacement. */
      panneau.style.setProperty("--jardin-x", (dx * -13).toFixed(1) + "px");
      panneau.style.setProperty("--jardin-y", (dy * -5).toFixed(1) + "px");
    });
  });
  panneau.addEventListener("mouseleave", function () {
    ["--parallaxe-x", "--parallaxe-y", "--jardin-x", "--jardin-y"].forEach(function (v) {
      panneau.style.setProperty(v, "0px");
    });
  });
  }
})();
