---
id: geo.contentInInitialHtml
evaluator: threshold
domain: geo
severity: critical
fixer: developer
unit: words
minimum: 50
label:
  en: "Real text in the initial HTML"
  fr: "Du vrai texte dans le HTML initial"
title:
  en: "The initial HTML has {{value}} words (minimum {{min}})"
  fr: "Le HTML initial a {{value}} mots (minimum {{min}})"
advice:
  fr: "Rendez le texte de la page sur le serveur, car un robot n'exécute pas JavaScript."
---
Render the text of the page on the server, because a crawler does not run JavaScript.
