package com.dockmind.common.security;

import java.util.Set;

/**
 * The three DockMind roles, as they appear in the access token.
 * <ul>
 *   <li>{@code VIEWER}: read schedules, shipments and history</li>
 *   <li>{@code DISPATCHER}: everything a viewer can, plus book, update and cancel shipments</li>
 *   <li>{@code ADMIN}: everything a dispatcher can, plus warehouse and user settings (later milestones)</li>
 * </ul>
 */
public final class Roles {

    public static final String VIEWER = "VIEWER";
    public static final String DISPATCHER = "DISPATCHER";
    public static final String ADMIN = "ADMIN";

    /** Only these become Spring authorities; any other role in a token is ignored. */
    public static final Set<String> KNOWN = Set.of(VIEWER, DISPATCHER, ADMIN);

    private Roles() {
    }
}
