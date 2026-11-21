---
id: links.blankNoopener
evaluator: links.blankNoopener
subdomains: [links]
severity: minor
fixer: developer
label:
  en: "Links that open a new tab"
  fr: "Liens qui ouvrent un nouvel onglet"
title:
  en:
    one: "{{count}} link opens a new tab without rel=\"noopener\""
    other: "{{count}} links open a new tab without rel=\"noopener\""
  fr:
    one: "{{count}} lien ouvre un nouvel onglet sans rel=\"noopener\""
    other: "{{count}} liens ouvrent un nouvel onglet sans rel=\"noopener\""
advice:
  fr: "Ajoutez rel=\"noopener\" aux liens qui ouvrent un nouvel onglet."
---
Add rel="noopener" to the links that open a new tab.
