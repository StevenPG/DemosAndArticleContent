package com.example;

// Stores a plugin's display name and fully-qualified class name.
// Class names retrieved from the database are passed to Class.forName() at runtime —
// GraalVM's static analysis can't follow that string, so they need explicit metadata.
public class PluginRegistration {

    private final String name;
    private final String className;

    public PluginRegistration(String name, String className) {
        this.name = name;
        this.className = className;
    }

    public String getName()      { return name; }
    public String getClassName() { return className; }
}
