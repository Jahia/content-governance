---
id: html.imageAltMissing
evaluator: html.imageAltMissing
checkType: CODE
subdomains: [accessibility, geo.score]
severity: critical
fixer: editor
label:
  en: "Text alternatives of images"
  fr: "Alternatives textuelles des images"
title:
  en:
    one: "{{count}} image has no text alternative"
    other: "{{count}} images have no text alternative"
  fr:
    one: "{{count}} image sans alternative textuelle"
    other: "{{count}} images sans alternative textuelle"
advice:
  fr: "Donnez à chaque image un attribut alt : une description, ou une valeur vide pour une image décorative."
---
Give every image an alt attribute: a description, or an empty value for a decorative image.
