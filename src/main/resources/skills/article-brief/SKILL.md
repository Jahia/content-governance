---
name: article-brief
description: Write the brief of a new article for this site before anyone drafts it - the angle, the reader, the plan, the questions of the reader, the keywords, the call to action and the internal links. Use when an editor asks for a brief, an outline or a plan of an article, a blog post or a news item.
license: MIT
metadata:
  jahia-action: find
  jahia-activation: manual
  jahia-consumers: chat,mcp
  jahia-label: Article brief
  jahia-scope: write the brief of a new article, blog post or news item before anyone drafts it.
  jahia-in-scope:
    - Write a brief for an article about our new partner program.
    - Prépare le plan d'un billet de blog sur la sécurité.
  jahia-out-of-scope:
    - message: Write the whole article about our partner program.
      note: a full draft, not a brief
---

# Article brief

You write the brief of ONE article. You do not write the article.

1. Read the request: the subject, the reader and the goal. When the user names no reader, write for the reader of the brand frame. When the subject is missing, ask for it and stop.
2. Find what the site already says on the subject: call page.search_text with the words of the subject in the edit language, with their forms and synonyms joined with OR. Keep at most 5 pages.
3. Write the brief in the answer language, with these sections and nothing else:
   - **Angle**: one sentence, the point of the article for this reader.
   - **Reader**: who reads it, and what they already know.
   - **Title ideas**: three titles of 60 characters or fewer.
   - **Plan**: 4 to 6 H2 headings, each with one line on what it says.
   - **Questions of the reader**: 3 to 5, each answered somewhere in the plan.
   - **Keywords**: one main keyword and 3 to 5 related ones, in the edit language.
   - **Call to action**: one, with the page it leads to.
   - **Internal links**: each page found in step 2 that the article should link to, with its full path.
   - **Length**: a target in words.
4. Follow the voice and the writing rules of the brand frame in every line of the brief, the titles included.
5. End with one question: should the assistant create the page of the article from this brief?

This brief may be longer than a usual answer: at most 450 words.
