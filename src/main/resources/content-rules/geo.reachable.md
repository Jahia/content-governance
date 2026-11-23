---
id: geo.reachable
evaluator: geo.reachable
checkType: CODE
subdomain: geo.crawlers
severity: critical
fixer: administrator
label:
  en: "Every AI crawler gets the page"
  fr: "Chaque robot d'IA obtient la page"
title:
  en:
    one: "{{count}} AI crawler is refused"
    other: "{{count}} AI crawlers are refused"
  fr:
    one: "{{count}} robot d'IA est refusé"
    other: "{{count}} robots d'IA sont refusés"
advice:
  fr: "Laissez chaque robot d'IA obtenir la page : un refus vient souvent d'un pare-feu ou d'une protection contre les robots."
---
Let every AI crawler get the page: a refusal usually comes from a firewall or a bot-protection rule.
