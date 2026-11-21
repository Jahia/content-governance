---
id: readability.longSentences
evaluator: threshold
domain: readability
severity: minor
fixer: editor
unit: words
maximum: 25
label:
  en: "Length of sentences"
  fr: "Longueur des phrases"
title:
  en: "The longest sentence has {{value}} words (limit {{max}})"
  fr: "La phrase la plus longue a {{value}} mots (limite {{max}})"
advice:
  fr: "Découpez les phrases plus longues que la limite."
---
Split the sentences that are longer than the limit.
