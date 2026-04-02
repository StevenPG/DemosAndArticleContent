package com.example;

// Added in the "iterative pattern" section of the article.
// This plugin is intentionally added WITHOUT a test first to demonstrate
// the stale-config failure — then a test is added to fix it.
public class WavePlugin implements Plugin {
    @Override
    public String run() {
        return "Wave from WavePlugin";
    }
}
