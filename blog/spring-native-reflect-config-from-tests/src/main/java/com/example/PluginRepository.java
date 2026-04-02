package com.example;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PluginRepository {

    private final JdbcTemplate jdbc;

    public PluginRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(PluginRegistration reg) {
        jdbc.update(
            "INSERT INTO plugin_registrations (name, class_name) VALUES (?, ?)",
            reg.getName(), reg.getClassName()
        );
    }

    public Optional<PluginRegistration> findByName(String name) {
        return jdbc.query(
            "SELECT name, class_name FROM plugin_registrations WHERE name = ?",
            (rs, rowNum) -> new PluginRegistration(rs.getString("name"), rs.getString("class_name")),
            name
        ).stream().findFirst();
    }

    public List<PluginRegistration> findAll() {
        return jdbc.query(
            "SELECT name, class_name FROM plugin_registrations",
            (rs, rowNum) -> new PluginRegistration(rs.getString("name"), rs.getString("class_name"))
        );
    }

    public void deleteAll() {
        jdbc.update("DELETE FROM plugin_registrations");
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM plugin_registrations", Long.class);
        return n == null ? 0 : n;
    }
}
