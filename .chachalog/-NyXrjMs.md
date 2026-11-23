---
# Allowed version bumps: patch, minor, major
content-governance: major
---

Added a way for other modules to add Page quality checks that run in the browser, on the preview of the page.

A rule states that it runs in the browser in its file, or with **Runs in** on its node, and it never stops a publication. Accessibility, Web Vitals and Ecodesign now run this way. A rule whose check no installed module provides shows under **Not checked** in its tab. The Ecodesign tab lists 11 criteria and can highlight each image it reports. An eco-design rule that an administrator changed in the system site keeps its limits and moves to the browser when the module starts. If your own module marked a check as a browser check on the server, state `runtime: browser` on its rules instead. Six accessibility rules that axe-core marks experimental, such as `p-as-heading` and `td-has-header`, are now off by default: switch one on in the layout of the site to keep it.
