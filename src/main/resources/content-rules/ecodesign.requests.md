---
id: ecodesign.requests
evaluator: ecodesign.requests
checkType: CODE
runtime: browser
subdomain: ecodesign
severity: moderate
fixer: developer
unit: requests
maximum: 70
label:
  en: "Number of requests"
  fr: "Nombre de requêtes"
title:
  en: "The page makes {{count}} requests (limit {{max}})"
  fr: "La page fait {{count}} requêtes (limite {{max}})"
advice:
  fr: "Réduisez le nombre de fichiers que la page demande."
---
Reduce the number of files the page requests.
