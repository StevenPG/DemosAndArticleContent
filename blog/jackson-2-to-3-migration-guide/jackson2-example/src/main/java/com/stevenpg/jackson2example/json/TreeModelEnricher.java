package com.stevenpg.jackson2example.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stevenpg.jackson2example.model.BlogPost;
import org.springframework.stereotype.Component;

/**
 * Demonstrates the TREE MODEL API: converting a POJO to a mutable
 * {@link JsonNode} graph, editing it, and returning the result directly -
 * useful when you need to add computed fields without changing the POJO
 * itself (e.g. an API gateway enriching a response it doesn't own).
 *
 * <p><b>Migration note:</b> {@code JsonNode}/{@code ObjectNode} live in
 * {@code com.fasterxml.jackson.databind[.node]} in Jackson 2, moving to
 * {@code tools.jackson.databind[.node]} in Jackson 3 - method names
 * ({@code put}, {@code valueToTree}, {@code get}, ...) are unchanged. This
 * class has no checked exceptions to begin with, so unlike
 * {@link StreamingWriter} and {@link MoneySerializer}, the jackson3-example
 * sibling file is a pure package-import diff - nothing else changes.
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
