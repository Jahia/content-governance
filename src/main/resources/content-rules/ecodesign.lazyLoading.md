---
id: ecodesign.lazyLoading
evaluator: ecodesign.lazyLoading
runtime: browser
subdomains: [ecodesign]
severity: moderate
fixer: developer
label:
  en: "Lazy loading of images"
  fr: "Chargement différé des images"
title:
  en:
    one: "{{count}} image below the fold loads at once"
    other: "{{count}} images below the fold load at once"
  fr:
    one: "{{count}} image sous la ligne de flottaison se charge tout de suite"
    other: "{{count}} images sous la ligne de flottaison se chargent tout de suite"
advice:
  fr: "Chargez en différé les images sous la ligne de flottaison."
---
Load the images below the fold lazily.
