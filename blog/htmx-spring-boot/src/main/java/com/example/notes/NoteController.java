package com.example.notes;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Every handler returns either the full page or a Thymeleaf *fragment*. HTMX
 * swaps the fragment into the DOM — that's the entire architecture.
 */
@Controller
public class NoteController {

    private final NoteStore store;

    public NoteController(NoteStore store) {
        this.store = store;
    }

    @GetMapping("/")
    String index(Model model) {
        model.addAttribute("notes", store.findAll(null));
        return "index";
    }

    /** Active search: hx-get from the search box returns just the list. */
    @GetMapping("/notes")
    String search(@RequestParam(defaultValue = "") String q, Model model) {
        model.addAttribute("notes", store.findAll(q));
        return "fragments :: note-list";
    }

    @PostMapping("/notes")
    String create(@RequestParam String title, @RequestParam String body, Model model) {
        store.create(title, body);
        model.addAttribute("notes", store.findAll(null));
        return "fragments :: note-list";
    }

    /** A single card in read mode — also the target of "Cancel". */
    @GetMapping("/notes/{id}")
    String card(@PathVariable long id, Model model) {
        model.addAttribute("note", store.find(id));
        return "fragments :: note-card";
    }

    /** Inline edit: swap a single card for its edit form. */
    @GetMapping("/notes/{id}/edit")
    String editForm(@PathVariable long id, Model model) {
        model.addAttribute("note", store.find(id));
        return "fragments :: note-edit";
    }

    @PutMapping("/notes/{id}")
    String update(@PathVariable long id, @RequestParam String title,
                  @RequestParam String body, Model model) {
        model.addAttribute("note", store.update(id, title, body));
        return "fragments :: note-card";
    }

    @DeleteMapping("/notes/{id}")
    @ResponseBody
    String delete(@PathVariable long id) {
        store.delete(id);
        // Empty body + outerHTML swap removes the card from the DOM
        return "";
    }
}
