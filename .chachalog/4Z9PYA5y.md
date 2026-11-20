---
# Allowed version bumps: patch, minor, major
content-governance: major
---

Removed every AI feature: the module calls no model, and works the same with no AI module installed.

The AI review of a page, its SEO texts, alternative texts and plain-language rewrites come from the AI Assistant module, in its own drawer tab. The written GEO report comes back once a module that writes it is installed. Delete org.jahia.community.contentgovernance.llm.cfg from the server, after you copy AI_PROMPT_APPENDIX into review.instructions of org.jahia.modules.aiassistant.cfg. Jahia 8.2.2 is required.
