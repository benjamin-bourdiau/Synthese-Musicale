import fs from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

const workspace = "G:/Clases/BUT3/Projet BUT3/appTest/app_BL/outputs/manual-20260602-cdd-cdi/presentations/droits-cdd-cdi";
const sourcePptx = `${workspace}/input/source.pptx`;
const finalPptx = `${workspace}/output/droits-jeune-diplome-cdd-cdi.pptx`;
const utilsUrl = pathToFileURL("C:/Users/benji/.codex/plugins/cache/openai-primary-runtime/presentations/26.601.10930/skills/presentations/scripts/artifact_tool_utils.mjs").href;

const { importArtifactTool, createSlideContext } = await import(utilsUrl);
const artifact = await importArtifactTool(workspace);
const { PresentationFile, FileBlob } = artifact;
const ctx = createSlideContext(artifact, {
  workspaceDir: workspace,
  slideSize: { width: 1920, height: 1080 },
  bodyFont: "Open Sans",
  titleFont: "Open Sans",
});

const presentation = await PresentationFile.importPptx(await FileBlob.load(sourcePptx));
const slides = presentation.slides.items;

const COLORS = {
  ink: "#111111",
  muted: "#4B5563",
  burgundy: "#8E2F2A",
  pale: "#F7F1F0",
  line: "#D9C9C7",
  green: "#0F766E",
};

function addText(slide, text, left, top, width, height, opts = {}) {
  return ctx.addText(slide, {
    text,
    left,
    top,
    width,
    height,
    fontSize: opts.fontSize ?? 28,
    color: opts.color ?? COLORS.ink,
    bold: opts.bold ?? false,
    typeface: "Open Sans",
    align: opts.align ?? "left",
    valign: opts.valign ?? "top",
    fill: opts.fill ?? "#00000000",
    line: opts.line ?? { style: "solid", fill: "#00000000", width: 0 },
    insets: opts.insets ?? { left: 12, right: 12, top: 8, bottom: 8 },
    name: opts.name,
  });
}

function addBox(slide, left, top, width, height, opts = {}) {
  return ctx.addShape(slide, {
    left,
    top,
    width,
    height,
    geometry: "rect",
    fill: opts.fill ?? "#FFFFFF",
    line: opts.line ?? { style: "solid", fill: COLORS.line, width: 1 },
    name: opts.name,
  });
}

function addBulletList(slide, items, left, top, width, opts = {}) {
  const gap = opts.gap ?? 86;
  items.forEach((item, i) => {
    const y = top + i * gap;
    addText(slide, "•", left, y + 2, 32, 46, { fontSize: opts.bulletSize ?? 30, color: COLORS.burgundy, bold: true });
    addText(slide, item, left + 42, y, width - 42, opts.rowHeight ?? 66, {
      fontSize: opts.fontSize ?? 25,
      color: opts.color ?? COLORS.ink,
      insets: { left: 0, right: 8, top: 4, bottom: 4 },
    });
  });
}

function addCallout(slide, text, left, top, width, height, opts = {}) {
  addBox(slide, left, top, width, height, {
    fill: opts.fill ?? COLORS.pale,
    line: { style: "solid", fill: opts.line ?? COLORS.burgundy, width: 2 },
  });
  addText(slide, text, left + 18, top + 14, width - 36, height - 28, {
    fontSize: opts.fontSize ?? 25,
    color: opts.color ?? COLORS.ink,
    bold: opts.bold ?? false,
    insets: { left: 0, right: 0, top: 0, bottom: 0 },
  });
}

function addTable(slide, columns, rows, left, top, width, rowHeight, opts = {}) {
  const headerH = opts.headerHeight ?? rowHeight;
  const colWidths = opts.colWidths ?? columns.map(() => width / columns.length);
  let x = left;
  columns.forEach((col, i) => {
    addBox(slide, x, top, colWidths[i], headerH, { fill: COLORS.burgundy, line: { style: "solid", fill: "#FFFFFF", width: 1 } });
    addText(slide, col, x + 8, top + 8, colWidths[i] - 16, headerH - 16, {
      fontSize: opts.headerFontSize ?? 20,
      color: "#FFFFFF",
      bold: true,
      align: "center",
      valign: "middle",
      insets: { left: 2, right: 2, top: 0, bottom: 0 },
    });
    x += colWidths[i];
  });
  rows.forEach((row, r) => {
    x = left;
    const y = top + headerH + r * rowHeight;
    row.forEach((cell, c) => {
      addBox(slide, x, y, colWidths[c], rowHeight, {
        fill: r % 2 === 0 ? "#FFFFFF" : COLORS.pale,
        line: { style: "solid", fill: COLORS.line, width: 1 },
      });
      addText(slide, cell, x + 10, y + 8, colWidths[c] - 20, rowHeight - 16, {
        fontSize: opts.bodyFontSize ?? 18,
        color: COLORS.ink,
        bold: c === 0,
        valign: "middle",
        insets: { left: 0, right: 0, top: 0, bottom: 0 },
      });
      x += colWidths[c];
    });
  });
}

// Slide 1 - title image, reusing an existing legal illustration embedded in the deck.
await ctx.addImage(slides[0], {
  path: `${workspace}/template-inspect/assets/ppt/media/image8.png`,
  left: 108,
  top: 185,
  width: 310,
  height: 310,
  fit: "contain",
  alt: "Illustration juridique",
  name: "Title legal illustration",
});
addCallout(slides[0], "Objectif : savoir quoi vérifier avant de signer, pendant le contrat et à la fin.", 470, 705, 990, 84, {
  fontSize: 25,
  fill: "#FFFFFF",
});

// Slide 2 - keep agenda, add purpose.
addCallout(slides[1], "Fil rouge : repérer les clauses utiles, les droits communs CDD/CDI et les pièges fréquents d’un premier emploi.", 1090, 330, 610, 210, {
  fontSize: 24,
  fill: COLORS.pale,
});

// Slide 3 - CDD overview.
addBulletList(slides[2], [
  "Le CDD est l’exception : il sert à une tâche précise et temporaire, pas à occuper durablement un poste permanent.",
  "Il doit être écrit, en français, signé et transmis dans les 2 jours ouvrables suivant l’embauche.",
  "Le motif, le terme, la durée, le poste, la rémunération et la convention collective doivent être vérifiables.",
  "Si le cadre légal n’est pas respecté, le salarié peut demander la requalification du CDD en CDI."
], 105, 245, 1050, { fontSize: 24, gap: 92, rowHeight: 76 });
addCallout(slides[2], "À retenir : un CDD “oral” ou au motif flou est un signal d’alerte.", 1180, 420, 520, 190, { fontSize: 28, bold: true, fill: "#FFFFFF" });

// Slide 4 - CDD rights.
addBulletList(slides[3], [
  "Pendant le contrat, le salarié en CDD est intégré dans l’entreprise et bénéficie en principe des mêmes droits que les CDI.",
  "Temps de travail, repos, congés payés, bulletin de paie, protection santé-sécurité : les règles de base restent applicables.",
  "À la fin du CDD, la prime de précarité est en principe de 10 % de la rémunération brute totale, sauf exceptions.",
  "L’employeur doit remettre certificat de travail, solde de tout compte et attestation France Travail."
], 90, 220, 980, { fontSize: 23, gap: 90, rowHeight: 76 });
addCallout(slides[3], "Piège : pas de prime de précarité si le CDD se poursuit en CDI ou si un CDI équivalent est refusé.", 1110, 710, 650, 126, { fontSize: 24, fill: COLORS.pale });

// Slide 5 - CDI overview.
addBulletList(slides[4], [
  "Le CDI est la forme normale du contrat de travail : il n’a pas de date de fin prévue.",
  "Un CDI à temps plein n’est pas toujours obligatoirement écrit, mais un écrit reste fortement conseillé ; le temps partiel doit être écrit.",
  "La période d’essai doit être prévue dès le départ : 2 mois ouvriers/employés, 3 mois techniciens/agents de maîtrise, 4 mois cadres.",
  "Un CDI peut se rompre par démission, licenciement, rupture conventionnelle ou pendant la période d’essai."
], 95, 230, 900, { fontSize: 23, gap: 92, rowHeight: 78 });
addCallout(slides[4], "Pour un jeune diplômé, vérifier surtout : statut cadre/non-cadre, forfait jours, rémunération et convention collective.", 1050, 565, 590, 170, { fontSize: 25, fill: "#FFFFFF" });

// Slide 6 - CDI rights.
addBulletList(slides[5], [
  "Le salaire doit au minimum respecter le Smic et, si elle est plus favorable, la grille de la convention collective.",
  "Les congés payés s’acquièrent à raison de 2,5 jours ouvrables par mois de travail effectif.",
  "La durée légale est de 35 h/semaine ; les heures au-delà sont des heures supplémentaires sauf régime particulier.",
  "En CDI, l’ancienneté compte notamment pour préavis, indemnités, avantages conventionnels et protection en cas de rupture."
], 100, 240, 980, { fontSize: 23, gap: 90, rowHeight: 76 });
addCallout(slides[5], "Point de vigilance : le forfait jours exige un accord écrit et des garanties de repos.", 1120, 685, 590, 130, { fontSize: 26, fill: COLORS.pale });

// Slide 7 - CDD seniority table.
addTable(slides[6],
  ["Moment", "Droits / effets", "À vérifier", "Piège à éviter"],
  [
    ["Avant / J1", "Contrat écrit, motif précis, durée ou terme.", "Signature, poste, salaire, convention.", "Commencer sans contrat CDD signé."],
    ["Essai", "Max. 1 jour/semaine ; 2 semaines si CDD ≤ 6 mois, 1 mois au-delà.", "Clause d’essai prévue au contrat.", "Croire qu’elle peut être renouvelée."],
    ["Pendant", "Droits proches du CDI : congés, paie, sécurité, temps de travail.", "Bulletins, heures, repos, mutuelle.", "Accepter des heures non tracées."],
    ["Fin", "Prime précarité 10 % + congés payés si dus.", "Exceptions : CDI, saisonnier, faute, refus CDI équivalent.", "Oublier les documents de fin."],
    ["Après 6 mois", "Demande possible d’information sur les postes CDI disponibles.", "Demande écrite avec date certaine.", "Ne pas garder de preuve écrite."]
  ],
  90, 230, 1740, 116,
  { colWidths: [250, 540, 440, 510], bodyFontSize: 18, headerFontSize: 21, headerHeight: 58 }
);

// Slide 8 - CDI seniority table.
addTable(slides[7],
  ["Ancienneté", "Ce qui change", "Droit concret", "Réflexe utile"],
  [
    ["Embauche", "Le CDI commence sans terme prévu.", "Contrat écrit conseillé ; obligatoire à temps partiel.", "Demander fiche de poste et convention."],
    ["Période d’essai", "Durée selon catégorie ; renouvellement seulement sous conditions.", "Délai de prévenance si rupture par l’employeur.", "Noter la date exacte de fin d’essai."],
    ["Chaque mois", "Congés payés acquis progressivement.", "2,5 jours ouvrables par mois de travail effectif.", "Suivre son compteur de congés."],
    ["À partir de 8 mois", "En cas de licenciement hors faute grave/lourde.", "Droit possible à l’indemnité légale de licenciement.", "Comparer légal, conventionnel, contrat."],
    ["À la rupture", "Préavis et indemnités varient selon situation.", "Documents fin de contrat remis par l’employeur.", "Ne signer le solde qu’après vérification."]
  ],
  90, 230, 1740, 116,
  { colWidths: [250, 540, 440, 510], bodyFontSize: 18, headerFontSize: 21, headerHeight: 58 }
);

// Slide 9 - mistakes.
addBulletList(slides[8], [
  "Signer sans lire la convention collective, le statut, la durée du travail et les clauses de mobilité / exclusivité.",
  "Confondre salaire brut, net et coût employeur : vérifier la fiche de paie et les primes annoncées.",
  "Refuser un CDI équivalent à la fin d’un CDD sans mesurer les effets possibles sur prime de précarité et France Travail.",
  "Signer le reçu pour solde de tout compte sans vérifier : contestation limitée à 6 mois si le reçu est signé."
], 130, 240, 1180, { fontSize: 25, gap: 110, rowHeight: 86 });
addCallout(slides[8], "Bon réflexe : tout accord important se confirme par écrit.", 1330, 490, 430, 130, { fontSize: 29, bold: true, fill: "#FFFFFF" });

// Slide 10 - behavior checklist.
addBulletList(slides[9], [
  "Avant signature : lire contrat + convention collective, vérifier salaire, durée du travail, période d’essai et lieu.",
  "Pendant : conserver contrat, avenants, bulletins, planning, mails sur heures supplémentaires ou primes.",
  "À la fin : demander les 3 documents obligatoires et contrôler congés payés, prime éventuelle, préavis et solde.",
  "En cas de doute : contacter le CSE, l’inspection du travail, un syndicat, le Défenseur des droits ou le conseil de prud’hommes."
], 130, 220, 1220, { fontSize: 24, gap: 108, rowHeight: 86 });
addCallout(slides[9], "Méthode simple : “écrit + date + preuve”.", 1360, 345, 390, 120, { fontSize: 30, bold: true, fill: COLORS.pale });

// Slide 11 - sources.
addText(slides[10], "Sources consultées le 2 juin 2026", 90, 145, 980, 48, { fontSize: 28, bold: true, color: COLORS.burgundy });
addBulletList(slides[10], [
  "Service-Public.fr – Conclusion du CDD : écrit, délai de transmission, mentions obligatoires.",
  "Service-Public.fr – Droits du salarié en CDD : égalité de traitement, CDI disponibles.",
  "Ministère du Travail – Le contrat à durée déterminée : recours, prime de précarité, refus de CDI.",
  "Service-Public.fr – Conclusion du CDI et période d’essai : écrit, durées, renouvellement.",
  "Service-Public.fr – Congés payés, temps de travail, fiche de paie, documents de fin de contrat.",
  "Service-Public.fr – Indemnité de licenciement, rupture CDI, solde de tout compte, prud’hommes."
], 100, 220, 1320, { fontSize: 21, gap: 70, rowHeight: 56, bulletSize: 26 });
addText(slides[10],
  "Liens : service-public.fr/F36, /F41, /F1906, /F1643, /F2258, /F1911, /F559, /F21789, /F987, /F86, /F2360 ; travail-emploi.gouv.fr – CDD.",
  95, 805, 1480, 110,
  { fontSize: 18, color: COLORS.muted, insets: { left: 0, right: 0, top: 0, bottom: 0 } }
);

await fs.mkdir(path.dirname(finalPptx), { recursive: true });
const pptx = await PresentationFile.exportPptx(presentation);
await pptx.save(finalPptx);
console.log(finalPptx);
