package com.meicrypt.identity.oauth.service;

import com.meicrypt.identity.oauth.entity.OAuthSigningKey;
import com.meicrypt.identity.oauth.entity.OAuthSigningKeyStatus;
import com.meicrypt.identity.oauth.repository.OAuthSigningKeyRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Module 7.2 - RSA signing key manager.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Bootstrap an RSA-2048 key pair on first startup and persist it under
 *       {@link OAuthSigningKeyStatus#ACTIVE}.</li>
 *   <li>Cache decoded {@link java.security.KeyPair} instances in-memory so the
 *       hot signing path never touches the database.</li>
 *   <li>Expose the currently-ACTIVE signer (used by
 *       {@link OAuthTokenGenerator}) and the publicly visible key set
 *       (used by {@code /.well-known/jwks.json}).</li>
 * </ul>
 *
 * <p>Rotation strategy: {@link #rotate()} generates a fresh ACTIVE key and
 * demotes the previous one to {@link OAuthSigningKeyStatus#ROTATED}, so
 * tokens signed under the old {@code kid} continue to verify via JWKS until
 * they expire.
 */
@Service
public class OAuthSigningKeyService {

    private static final Logger logger = LoggerFactory.getLogger(OAuthSigningKeyService.class);
    private static final String ALGORITHM = "RSA";
    private static final int KEY_SIZE_BITS = 2048;

    private final OAuthSigningKeyRepository repository;
    private final ConcurrentHashMap<String, KeyPair> keyCache = new ConcurrentHashMap<>();

    private volatile String activeKid;

    public OAuthSigningKeyService(OAuthSigningKeyRepository repository) {
        this.repository = repository;
    }

    // ---------------------------------------------------------------------
    // Bootstrap
    // ---------------------------------------------------------------------

    @PostConstruct
    @Transactional
    public void bootstrap() {
        Optional<OAuthSigningKey> active =
                repository.findFirstByStatusOrderByCreatedAtDesc(OAuthSigningKeyStatus.ACTIVE);
        if (active.isPresent()) {
            this.activeKid = active.get().getKid();
            logger.info("Loaded existing ACTIVE OAuth signing key kid={}", activeKid);
        } else {
            OAuthSigningKey created = generateAndStore();
            this.activeKid = created.getKid();
            logger.info("Bootstrapped new ACTIVE OAuth signing key kid={}", activeKid);
        }
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * @return the currently-active signing key. Guaranteed non-null once
     *         {@link #bootstrap()} has completed.
     */
    public SigningKeyView getActiveSigningKey() {
        OAuthSigningKey entity = repository.findByKid(activeKid)
                .orElseThrow(() -> new IllegalStateException(
                        "Active signing key kid=" + activeKid + " missing from database"));
        return toView(entity);
    }

    /**
     * @return every key currently exposed publicly (ACTIVE + ROTATED).
     */
    public List<SigningKeyView> getPublishableKeys() {
        return repository.findAllByStatusIn(
                        List.of(OAuthSigningKeyStatus.ACTIVE, OAuthSigningKeyStatus.ROTATED))
                .stream()
                .map(this::toView)
                .toList();
    }

    /**
     * Rotate the signing key. The previously ACTIVE key becomes ROTATED so
     * outstanding tokens remain verifiable until they expire.
     */
    @Transactional
    public SigningKeyView rotate() {
        repository.findFirstByStatusOrderByCreatedAtDesc(OAuthSigningKeyStatus.ACTIVE)
                .ifPresent(existing -> {
                    existing.setStatus(OAuthSigningKeyStatus.ROTATED);
                    existing.setRotatedAt(Instant.now());
                    repository.save(existing);
                });
        OAuthSigningKey created = generateAndStore();
        this.activeKid = created.getKid();
        logger.info("Rotated OAuth signing key. New ACTIVE kid={}", activeKid);
        return toView(created);
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private OAuthSigningKey generateAndStore() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            generator.initialize(KEY_SIZE_BITS);
            KeyPair pair = generator.generateKeyPair();

            String kid = "mei-" + UUID.randomUUID().toString().replace("-", "");
            String privateB64 = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
            String publicB64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());

            OAuthSigningKey entity = new OAuthSigningKey(kid, privateB64, publicB64);
            entity = repository.save(entity);
            keyCache.put(kid, pair);
            return entity;
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("RSA key generation unavailable", ex);
        }
    }

    private SigningKeyView toView(OAuthSigningKey entity) {
        KeyPair pair = keyCache.computeIfAbsent(entity.getKid(), k -> decode(entity));
        return new SigningKeyView(
                entity.getKid(),
                entity.getAlgorithm(),
                entity.getKeyType(),
                entity.getKeyUse(),
                (RSAPrivateKey) pair.getPrivate(),
                (RSAPublicKey) pair.getPublic(),
                entity.getStatus());
    }

    private KeyPair decode(OAuthSigningKey entity) {
        try {
            KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
            byte[] privBytes = Base64.getDecoder().decode(entity.getPrivateKeyPkcs8Base64());
            byte[] pubBytes = Base64.getDecoder().decode(entity.getPublicKeyX509Base64());
            PrivateKey priv = factory.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
            PublicKey pub = factory.generatePublic(new X509EncodedKeySpec(pubBytes));
            return new KeyPair(pub, priv);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("Unable to decode signing key kid=" + entity.getKid(), ex);
        }
    }

    /**
     * Immutable snapshot of a signing key handed to consumers (never exposes
     * the raw JPA entity).
     */
    public record SigningKeyView(
            String kid,
            String algorithm,
            String keyType,
            String keyUse,
            RSAPrivateKey privateKey,
            RSAPublicKey publicKey,
            OAuthSigningKeyStatus status
    ) { }
}
