# HTMX + Spring Boot Notes

Companion project for the article [HTMX + Spring Boot: Building a Web App Without a Frontend Framework](https://stevenpg.com/posts/htmx-spring-boot-no-frontend-framework).

A CachePad-style notes app — create, inline-edit, delete, and live search —
built with HTMX 2 and Thymeleaf fragments. No npm, no bundler, no JSON API,
no build step. The only JavaScript shipped is htmx itself (~14 kB gzipped),
served as a webjar.

## Running

```bash
./gradlew bootRun
```

Open [localhost:8080](http://localhost:8080) and keep the browser network tab
open — every interaction is a small HTML fragment response.

## The interesting files

- `NoteController.java` — handlers return Thymeleaf fragment names
  (`fragments :: note-card`); HTMX swaps them into the DOM
- `templates/fragments.html` — the three fragments: list, card, edit form
- `templates/index.html` — the full page; `hx-*` attributes are the entire
  "frontend"
