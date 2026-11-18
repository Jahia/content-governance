---
# Allowed version bumps: patch, minor, major
content-governance: major
---

Moved the AI features to genai-connector, which holds the provider, key and model; Jahia 8.2.2 is now required.

Install genai-connector and grant genaiAccess to the editors who use AI; without them the AI features stay greyed out. Remove AI_PROVIDER, AI_MODEL, AI_API_KEY and AI_BASE_URL from the module's llm.cfg on the server.
