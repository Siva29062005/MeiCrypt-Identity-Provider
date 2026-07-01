package com.meicrypt.identity.oauth.service;

import com.meicrypt.identity.application.entity.ClientApplication;
import com.meicrypt.identity.oauth.exception.OAuthException;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Module 6.2 - scope enforcement.
 *
 * <p>Reconciles the scopes requested by an OAuth client against the scopes
 * registered on the {@link ClientApplication}. Requested scopes MUST be a
 * subset of the registered scopes - any excess is rejected with
 * {@code invalid_scope}.
 */
@Service
public class ScopeService {

    /**
     * Returns the granted scope set (space-separated, canonical order) for a
     * requested {@code scope} parameter. If {@code requested} is null/blank,
     * the client's full registered scope list is granted (spec-compliant
     * fallback per RFC 6749 §3.3).
     */
    public String reconcile(ClientApplication client, String requested) {
        Set<String> registered = parse(client.getScopes());
        if (registered.isEmpty()) {
            throw OAuthException.invalidScope(
                    "Client has no registered scopes; contact organization administrator");
        }
        if (requested == null || requested.isBlank()) {
            return String.join(" ", registered);
        }
        Set<String> asked = parseSpaceOrComma(requested);
        Set<String> granted = new LinkedHashSet<>();
        for (String scope : asked) {
            if (!registered.contains(scope)) {
                throw OAuthException.invalidScope(
                        "Scope '" + scope + "' is not registered for this client");
            }
            granted.add(scope);
        }
        if (granted.isEmpty()) {
            throw OAuthException.invalidScope("No valid scope requested");
        }
        return String.join(" ", granted);
    }

    /**
     * Validates a set of scopes on refresh: the client cannot ask for scopes
     * beyond what was originally granted.
     */
    public String narrowOnRefresh(String originalScopes, String requested) {
        Set<String> original = parse(originalScopes);
        if (requested == null || requested.isBlank()) {
            return String.join(" ", original);
        }
        Set<String> asked = parseSpaceOrComma(requested);
        Set<String> granted = new LinkedHashSet<>();
        for (String scope : asked) {
            if (!original.contains(scope)) {
                throw OAuthException.invalidScope(
                        "Cannot broaden scope on refresh: '" + scope + "' was not granted");
            }
            granted.add(scope);
        }
        if (granted.isEmpty()) {
            throw OAuthException.invalidScope("No valid scope requested");
        }
        return String.join(" ", granted);
    }

    public Set<String> parse(String csvOrSpaced) {
        if (csvOrSpaced == null || csvOrSpaced.isBlank()) {
            return Collections.emptySet();
        }
        return parseSpaceOrComma(csvOrSpaced);
    }

    private Set<String> parseSpaceOrComma(String raw) {
        return new LinkedHashSet<>(Arrays.asList(raw.trim().split("[\\s,]+")));
    }
}
