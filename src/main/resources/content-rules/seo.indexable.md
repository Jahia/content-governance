---
id: seo.indexable
evaluator: seo.indexable
checkType: CODE
subdomain: seo
severity: critical
fixer: editor
label:
  en: "The page can be indexed"
  fr: "La page peut être indexée"
title:
  en: "The robots meta tag holds noindex ({{value}})"
  fr: "La balise meta robots contient noindex ({{value}})"
advice:
  fr: "Retirez noindex de la balise meta robots, sauf si la page doit rester hors des résultats de recherche."
---
Remove noindex from the robots meta tag, unless the page must stay out of search results.
