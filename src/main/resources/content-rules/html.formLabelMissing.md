---
id: html.formLabelMissing
evaluator: html.formLabelMissing
checkType: CODE
subdomain: accessibility
severity: critical
fixer: developer
label:
  en: "Labels of form fields"
  fr: "Libellés des champs de formulaire"
title:
  en:
    one: "{{count}} form field has no label"
    other: "{{count}} form fields have no label"
  fr:
    one: "{{count}} champ de formulaire sans libellé"
    other: "{{count}} champs de formulaire sans libellé"
advice:
  fr: "Donnez à chaque champ de formulaire un élément label, ou un attribut aria-label."
---
Give every form field a label element, or an aria-label attribute.
