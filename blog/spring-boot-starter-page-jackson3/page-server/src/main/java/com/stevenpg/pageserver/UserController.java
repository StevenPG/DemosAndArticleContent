package com.stevenpg.pageserver;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final List<User> USERS = List.of(
            new User(1L, "Alice",   "alice@example.com"),
            new User(2L, "Bob",     "bob@example.com"),
            new User(3L, "Charlie", "charlie@example.com"),
            new User(4L, "Diana",   "diana@example.com"),
            new User(5L, "Eve",     "eve@example.com"),
            new User(6L, "Frank",   "frank@example.com"),
            new User(7L, "Grace",   "grace@example.com"),
            new User(8L, "Hank",    "hank@example.com"),
            new User(9L, "Iris",    "iris@example.com"),
            new User(10L, "Jack",   "jack@example.com")
    );

    @GetMapping
    public Page<User> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size);
        int start = (int) pageable.getOffset();
        int end   = Math.min(start + size, USERS.size());

        if (start >= USERS.size()) {
            return new PageImpl<>(List.of(), pageable, USERS.size());
        }

        return new PageImpl<>(USERS.subList(start, end), pageable, USERS.size());
    }
}
