---
name: seo-review
description: Review the search engine optimization of one page of the site and propose fixes - the title, the description, the headings, the alternative texts of images and the internal links. Use when an editor asks for an SEO review, an SEO check or better search results for a page.
license: MIT
metadata:
  jahia-action: edit
  jahia-activation: auto
  jahia-consumers: chat,mcp
  jahia-label: SEO review
  jahia-scope: review the search engine optimization of one page of the site and propose fixes.
  jahia-in-scope:
    - Do an SEO review of this page.
    - Pourquoi cette page sort-elle mal dans Google ?
  jahia-out-of-scope:
    - message: Submit the site to Google Search Console.
      note: a task outside Jahia
---

# SEO review of a page

1. Find the page that the user names: currentPage when the user says "this page". Call page.outline on it with fullValues true.
2. Check these points, and note each finding with the path of the content item it concerns:
   - the title of the page: 30 to 60 characters, the main subject first (the default of the rule seo.title);
   - the description of the page, when the page has one: 50 to 160 characters, one sentence that makes a reader want to click (the default of the rule seo.metaDescription);
   - the headings: one main heading, then lower levels in order, and no heading that says nothing, such as "Introduction";
   - each image: an alternative text that says what the image shows;
   - the internal links: at least two links to other pages of the site, with a link text that says where it leads.
3. Never promise a gain from an llms.txt file or from cutting a text into small parts: the guide of Google on generative search says that neither helps.
4. Give at most 7 findings, the most important first. For each one, give the fix, written in the voice of the brand frame and within its writing rules.
5. When the user asks to apply the fixes, propose one content.update for each content item, which changes only the properties of the finding.

This review may be longer than a usual answer: at most 350 words.
