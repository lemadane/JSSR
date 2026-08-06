package com.jssr.e2e.app.repository;

import com.jssr.e2e.app.model.User;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class UserRepository {
    private final Map<Long, User> db = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(100);

    public UserRepository() {
        seedInitialData();
    }

    private void seedInitialData() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        save(new User(1L, "Sarah Connor", "sarah.connor@jssr.dev", "Admin", "ACTIVE", "2026-01-15"));
        save(new User(2L, "Alex Mercer", "alex.mercer@jssr.dev", "Developer", "ACTIVE", "2026-02-10"));
        save(new User(3L, "Elena Rostova", "elena.rostova@jssr.dev", "Designer", "INACTIVE", "2026-03-22"));
        save(new User(4L, "Marcus Vance", "marcus.vance@jssr.dev", "Developer", "ACTIVE", "2026-05-04"));
        save(new User(5L, "Chloe Bennett", "chloe.b@jssr.dev", "Product Lead", "ACTIVE", today));
    }

    public List<User> findAll() {
        return db.values().stream()
                .sorted(Comparator.comparing(User::id).reversed())
                .collect(Collectors.toList());
    }

    public Optional<User> findById(Long id) {
        return Optional.ofNullable(db.get(id));
    }

    public User save(User user) {
        Long id = user.id();
        if (id == null || id <= 0) {
            id = idSequence.incrementAndGet();
        }
        String createdAt = user.createdAt();
        if (createdAt == null || createdAt.isBlank()) {
            createdAt = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        User saved = new User(id, user.name(), user.email(), user.role(), user.status(), createdAt);
        db.put(id, saved);
        return saved;
    }

    public boolean deleteById(Long id) {
        return db.remove(id) != null;
    }

    public List<User> search(String query) {
        if (query == null || query.isBlank()) {
            return findAll();
        }
        String q = query.toLowerCase().trim();
        return findAll().stream()
                .filter(u -> u.name().toLowerCase().contains(q) ||
                             u.email().toLowerCase().contains(q) ||
                             u.role().toLowerCase().contains(q) ||
                             u.status().toLowerCase().contains(q))
                .collect(Collectors.toList());
    }

    public long countActive() {
        return db.values().stream().filter(User::isActive).count();
    }

    public long countAdmins() {
        return db.values().stream().filter(u -> "Admin".equalsIgnoreCase(u.role())).count();
    }

    public long countTotal() {
        return db.size();
    }
}
