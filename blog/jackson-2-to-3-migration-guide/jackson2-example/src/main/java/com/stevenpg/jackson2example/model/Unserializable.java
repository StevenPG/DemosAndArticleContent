package com.stevenpg.jackson2example.model;

/**
 * A deliberately cyclic structure: {@code self} points back to the same
 * instance, so serializing it would recurse forever.
 *
 * <p>Both Jackson 2 and Jackson 3 detect direct self-references and refuse
 * to serialize them by default (an "InvalidDefinitionException"), rather
 * than looping until a {@code StackOverflowError}. Used by
 * {@code /api/posts/broken/raw-json} to demonstrate that failure path -
 * see {@code JacksonErrorAdvice} for how each version's caller is forced
 * (or not forced) to handle it.
 *
 * <p><b>Migration footnote:</b> an earlier version of this demo used an
 * EMPTY class (no fields at all), since Jackson 2's
 * {@code FAIL_ON_EMPTY_BEANS} throws for that case by default. That default
 * changed in Jackson 3 - serializing a type with zero properties there now
 * produces {@code {}} instead of throwing. Self-reference detection, on the
 * other hand, is unchanged between versions, which is why this class uses
 * a cycle instead - it fails identically on both sides.
 */
public final class Unserializable {

    public String name = "root";
    public Unserializable self = this;
}
