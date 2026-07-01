package com.meicrypt.identity.application.entity;

/**
 * Classification of a client application (Phase 5, Module 5.1).
 *
 * The type controls whether a client is confidential (can keep a secret) or
 * public (cannot). This decision is enforced at credential issuance time in
 * {@link com.meicrypt.identity.application.service.ClientApplicationService}.
 */
public enum ApplicationType {
    /** Confidential server-side web app - keeps a client_secret. */
    WEB(true),

    /** Single-page browser app - public client, no secret, PKCE mandatory. */
    SPA(false),

    /** Native mobile client - public client, PKCE mandatory. */
    MOBILE(false),

    /** Machine-to-machine service account - confidential, client_credentials grant. */
    SERVICE(true);

    private final boolean confidential;

    ApplicationType(boolean confidential) {
        this.confidential = confidential;
    }

    public boolean isConfidential() {
        return confidential;
    }
}
