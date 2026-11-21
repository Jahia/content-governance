---
id: geo.sameContentForCrawlers
evaluator: threshold
domain: geo
severity: critical
fixer: administrator
unit: ratio
minimum: 0.5
label:
  en: "Crawlers get the same text as a browser"
  fr: "Les robots reçoivent le même texte qu'un navigateur"
title:
  en: "A crawler gets {{value}} of the text of a browser (minimum {{min}})"
  fr: "Un robot reçoit {{value}} du texte d'un navigateur (minimum {{min}})"
advice:
  fr: "Servez à un robot le même texte qu'à un navigateur : vérifiez ce que le serveur fait varier selon l'agent utilisateur."
---
Serve a crawler the same text as a browser: check what the server varies on the user agent.
