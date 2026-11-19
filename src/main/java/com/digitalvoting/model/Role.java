package com.digitalvoting.model;

import java.util.EnumSet;
import java.util.Set;

/** Admin roles and the permissions each one grants (shown on the "Roles and permissions" screen). */
public enum Role {
    SUPER_ADMIN(EnumSet.allOf(Permission.class)),
    ELECTION_OFFICER(EnumSet.of(Permission.MANAGE_VOTERS, Permission.MANAGE_CANDIDATES,
            Permission.MANAGE_ELECTIONS, Permission.VIEW_REPORTS)),
    AUDITOR(EnumSet.of(Permission.VIEW_REPORTS));

    public enum Permission { MANAGE_VOTERS, MANAGE_CANDIDATES, MANAGE_ELECTIONS, VIEW_REPORTS, MANAGE_ROLES }

    private final Set<Permission> permissions;
    Role(Set<Permission> p) { this.permissions = p; }
    public boolean can(Permission p) { return permissions.contains(p); }
}
