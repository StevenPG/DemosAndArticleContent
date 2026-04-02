package com.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/plugins")
public class PluginController {

    private final PluginLoader pluginLoader;
    private final PluginRepository pluginRepository;

    public PluginController(PluginLoader pluginLoader, PluginRepository pluginRepository) {
        this.pluginLoader = pluginLoader;
        this.pluginRepository = pluginRepository;
    }

    // Invoke a plugin by the name stored in the database.
    // Looks up the class name, then delegates to PluginLoader.
    @GetMapping("/invoke/{name}")
    public ResponseEntity<Map<String, String>> invoke(@PathVariable String name) throws Exception {
        var registration = pluginRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Plugin not found: " + name));

        String result = pluginLoader.invoke(registration.getClassName());
        return ResponseEntity.ok(Map.of("result", result));
    }
}
