---
# Allowed version bumps: patch, minor, major
content-governance: patch
---

Page quality no longer stays on "Loading" or "Analyzing" when part of a page never answers. A page whose images, fonts or scripts keep loading is audited after 20 seconds as it stands, and a check that takes too long (Accessibility 60 seconds, Links 45, Jahia 30, Web Vitals 15) or fails is stopped: its tab says why with a Re-run button, and the other tabs show their results. jContent also starts faster: it now loads about 105 kB of the module instead of 622 kB, with only the editor's language; the drawer loads in the background once its button shows, and the dashboards and settings the first time they are opened.
