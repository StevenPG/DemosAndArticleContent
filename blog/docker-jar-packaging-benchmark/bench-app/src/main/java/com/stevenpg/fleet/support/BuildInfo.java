package com.stevenpg.fleet.support;

/**
 * Stands in for the one-line application change every rebuild benchmark needs:
 * the harness rewrites REVISION, which is a real constant-pool change, so the
 * compiled class - and therefore the jar, and therefore the layers that contain
 * it - genuinely differ.
 *
 * <p>A comment-only edit would not work: javac strips comments, Spring Boot
 * normalises timestamps inside bootJar, and the rebuilt image comes out
 * bit-identical.
 */
public final class BuildInfo {

    public static final String REVISION = "dev";

    private BuildInfo() {}
}
