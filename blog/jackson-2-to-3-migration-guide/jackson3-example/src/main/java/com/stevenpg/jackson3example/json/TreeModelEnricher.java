package com.stevenpg.jackson3example.json;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import com.stevenpg.jackson3example.model.BlogPost;
import org.springframework.stereotype.Component;

/**
 * Demonstrates the TREE MODEL API - identical purpose to jackson2-example's
 * {@code TreeModelEnricher}.
 *
 * <p><b>Migration note:</b> {@code JsonNode}/{@code ObjectNode} move from
 * {@code com.fasterxml.jackson.databind[.node]} to
 * {@code tools.jackson.databind[.node]} - method names ({@code put},
 * {@code valueToTree}, {@code get}, ...) are unchanged. This class has no
 * checked exceptions in either version, so unlike {@code StreamingWriter}
 * and {@code MoneySerializer}, this file is a pure package-import diff from
 * its jackson2-example sibling - nothing else changes.
 */
@Component
public class TreeModelEnricher {

    private final ObjectMapper objectMapper;

    public TreeModelEnricher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode enrich(BlogPost post) {
        // Convert the POJO into a mutable tree instead of a JSON string.
        ObjectNode node = objectMapper.valueToTree(post);

        // Add a field that only makes sense at the API layer, without
        // touching the BlogPost class at all.
        node.put("wordCountEstimate", estimateWordCount(post));
        node.put("enrichedBy", "TreeModelEnricher");

        return node;
    }

    private int estimateWordCount(BlogPost post) {
        String title = post.getTitle();
        return title == null ? 0 : title.split("\\s+").length;
    }
}
