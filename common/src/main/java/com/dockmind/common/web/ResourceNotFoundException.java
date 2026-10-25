package com.dockmind.common.web;

/**
 * Thrown when a requested resource does not exist, or exists but belongs to another
 * tenant. Both cases return the same 404 so one tenant cannot discover which IDs
 * another tenant uses.
 *
 * <p>The message is returned to the client, so it must never contain data from
 * another tenant or internal details.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceType, Object id) {
        super(resourceType + " " + id + " was not found");
    }
}
