---
id: ecodesign.thirdParty
evaluator: threshold
subdomains: [ecodesign]
severity: moderate
fixer: developer
unit: hosts
maximum: 5
label:
  en: "Third-party hosts"
  fr: "Hôtes tiers"
title:
  en: "The page loads files from {{count}} third-party hosts (limit {{max}})"
  fr: "La page charge des fichiers de {{count}} hôtes tiers (limite {{max}})"
advice:
  fr: "Chargez les fichiers depuis moins d'hôtes tiers."
---
Load files from fewer third-party hosts.
