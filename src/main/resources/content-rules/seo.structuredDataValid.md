---
id: seo.structuredDataValid
evaluator: seo.structuredDataValid
checkType: CODE
subdomain: seo
severity: serious
fixer: developer
label:
  en: "Valid structured data"
  fr: "Données structurées valides"
title:
  en:
    one: "{{count}} JSON-LD block does not parse"
    other: "{{count}} JSON-LD blocks do not parse"
  fr:
    one: "{{count}} bloc JSON-LD ne se lit pas"
    other: "{{count}} blocs JSON-LD ne se lisent pas"
advice:
  fr: "Corrigez les blocs JSON-LD illisibles, car un moteur de recherche les ignore."
---
Fix the JSON-LD blocks that do not parse, because a search engine ignores them.
