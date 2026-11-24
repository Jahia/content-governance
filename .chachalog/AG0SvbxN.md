---
# Allowed version bumps: patch, minor, major
content-governance: minor
---

Fixed the conversion of the rules and the glossaries of an older version, which ran on every node of a cluster at once.

Only the processing server converts them, and seeds the tree of the system site with the shipped rules, when the module starts. A browsing node shares its repository and writes nothing then, so it never reads a node that the processing server is writing.
