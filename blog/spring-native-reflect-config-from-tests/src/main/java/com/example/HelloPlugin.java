package com.example;

// A concrete plugin loaded dynamically by class name.
// From GraalVM's perspective, this class is never referenced directly in the code —
// only its name (a String) is passed to Class.forName(). The compiler cannot see
// that this class will be instantiated, so it won't include it in reflection metadata
// unless we explicitly tell it to.
public class HelloPlugin implements Plugin {
    @Override
    public String run() {
        return "Hello from HelloPlugin";
    }
}
