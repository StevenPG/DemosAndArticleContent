package com.example;

import org.springframework.stereotype.Service;

// This is the reflection-heavy code that breaks native image without configuration.
//
// Class.forName() takes a String and returns a Class object at runtime.
// GraalVM's points-to analysis runs at BUILD time — it can't follow a String
// to know which class will be loaded. From the compiler's perspective, the target
// class is invisible. Without a reflect-config.json entry for that class,
// the native image won't include the class's constructor or methods in the
// reflection metadata, and they'll fail at runtime.
@Service
public class PluginLoader {

    public String invoke(String className) throws Exception {
        // This is the problematic line in a native image context.
        // Works perfectly on the JVM. Fails silently (or loudly) in native image
        // until the class is registered in reflect-config.json.
        Class<?> pluginClass = Class.forName(className);

        // getDeclaredConstructor().newInstance() is the modern replacement for
        // the deprecated Class.newInstance(). Both require the constructor to be
        // registered in reflect-config.json for native image.
        Plugin instance = (Plugin) pluginClass.getDeclaredConstructor().newInstance();

        return instance.run();
    }
}
