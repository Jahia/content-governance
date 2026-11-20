---
# Allowed version bumps: patch, minor, major
content-governance: minor
---

Fixed the conversion of the rules and the glossaries of an older version, which ran on every node of a cluster at once.

Only the processing server converts them when the module starts. A browsing node shares its repository and converts nothing, so it never reads a node that the processing server is converting.
