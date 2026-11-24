---
id: ecodesign.heavyAssets
evaluator: ecodesign.heavyAssets
checkType: CODE
runtime: browser
subdomain: ecodesign
severity: moderate
fixer: developer
unit: kilobytes
maximum: 300
label:
  en: "Weight of scripts and stylesheets"
  fr: "Poids des scripts et des feuilles de style"
title:
  en:
    one: "{{count}} script or stylesheet is over {{max}} KB, such as {{name}}"
    other: "{{count}} scripts or stylesheets are over {{max}} KB, such as {{name}}"
  fr:
    one: "{{count}} script ou feuille de style dépasse {{max}} Ko, comme {{name}}"
    other: "{{count}} scripts ou feuilles de style dépassent {{max}} Ko, comme {{name}}"
advice:
  fr: "Découpez ou réduisez les scripts et les feuilles de style qui dépassent la limite."
---
Split or reduce the scripts and stylesheets that are over the limit.
