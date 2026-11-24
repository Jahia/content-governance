---
id: readability.longParagraphs
evaluator: threshold
checkType: CODE
subdomain: readability
severity: moderate
fixer: editor
unit: words
maximum: 80
label:
  en: "Length of paragraphs"
  fr: "Longueur des paragraphes"
title:
  en: "Paragraphs have {{value}} words on average (limit {{max}})"
  fr: "Les paragraphes ont {{value}} mots en moyenne (limite {{max}})"
advice:
  fr: "Découpez les paragraphes, dont la longueur moyenne dépasse la limite."
---
Split the paragraphs, whose average length is over the limit.
