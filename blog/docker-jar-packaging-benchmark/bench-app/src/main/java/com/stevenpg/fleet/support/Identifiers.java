package com.stevenpg.fleet.support;

import java.util.concurrent.ThreadLocalRandom;

/** Deterministic-ish identifier helpers used by the seed data generator. */
public final class Identifiers {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";

    private Identifiers() {}

    public static String tail(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHABET.charAt(ThreadLocalRandom.current().nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }
}
