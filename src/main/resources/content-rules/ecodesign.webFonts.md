---
id: ecodesign.webFonts
evaluator: ecodesign.webFonts
runtime: browser
domain: ecodesign
severity: minor
fixer: developer
unit: files
maximum: 2
label:
  en: "Web fonts"
  fr: "Polices web"
title:
  en:
    one: "{{count}} font file is loaded (limit {{max}})"
    other: "{{count}} font files are loaded (limit {{max}})"
  fr:
    one: "{{count}} fichier de police est chargé (limite {{max}})"
    other: "{{count}} fichiers de police sont chargés (limite {{max}})"
advice:
  fr: "Chargez moins de fichiers de police, en WOFF2."
---
Load fewer font files, in WOFF2.
