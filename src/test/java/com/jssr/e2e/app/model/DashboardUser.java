package com.jssr.e2e.app.model;

import com.jssr.core.BooleanAttribute;
import java.util.List;
import java.util.Optional;

public record DashboardUser(
    Long id,
    String name,
    String email,
    String role,
    String status,
    boolean active,
    boolean emailVerified,
    boolean hasProSubscription,
    boolean isTeamOwner,
    int storageUsedMb,
    int unreadNotifications,
    Optional<String> bio,
    List<String> recentActivities,
    BooleanAttribute checkedAttribute
) {}
