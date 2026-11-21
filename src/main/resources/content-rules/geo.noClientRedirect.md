---
id: geo.noClientRedirect
evaluator: geo.noClientRedirect
subdomains: [geo.crawlers]
severity: critical
fixer: developer
label:
  en: "No client-side redirect"
  fr: "Pas de redirection côté client"
title:
  en: "The page redirects in the browser"
  fr: "La page redirige dans le navigateur"
advice:
  fr: "Remplacez le meta refresh par une redirection du serveur, qu'un robot suit."
---
Replace the meta refresh with a server redirect, which a crawler follows.
