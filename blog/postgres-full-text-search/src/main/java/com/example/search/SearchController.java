package com.example.search;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SearchController {

    private final SearchRepository repository;

    public SearchController(SearchRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/search/ilike")
    List<SearchResult> ilike(@RequestParam String q) {
        return repository.ilike(q);
    }

    @GetMapping("/search/fts")
    List<SearchResult> fullText(@RequestParam String q) {
        return repository.fullText(q);
    }

    @GetMapping("/search/fuzzy")
    List<SearchResult> fuzzy(@RequestParam String q) {
        return repository.fuzzy(q);
    }
}
