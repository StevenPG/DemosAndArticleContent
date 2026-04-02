package com.example;

// All plugin implementations must implement this interface.
// The PluginLoader loads them by class name and calls run() via reflection.
public interface Plugin {
    String run();
}
