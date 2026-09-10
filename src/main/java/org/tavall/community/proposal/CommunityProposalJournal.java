package org.tavall.community.proposal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only proposal journal compatible with the original TypeScript JSONL state format.
 *
 * <p>No mutable proposal map is retained by the application. Each operation derives a bounded
 * latest-state snapshot from the journal, then appends the next immutable state.</p>
 */
public final class CommunityProposalJournal {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final Path journalPath;
    private final byte[] signingSecret;
    private final Duration proposalTtl;
    private final Clock clock;

    public CommunityProposalJournal(
            ObjectMapper objectMapper,
            Path journalPath,
            String signingSecret,
            Duration proposalTtl,
            Clock clock
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.journalPath = Objects.requireNonNull(journalPath, "journalPath")
                .toAbsolutePath()
                .normalize();
        if (signingSecret == null || signingSecret.isBlank()) {
            throw new IllegalArgumentException("signingSecret must not be blank");
        }
        this.signingSecret = signingSecret.getBytes(StandardCharsets.UTF_8);
        this.proposalTtl = Objects.requireNonNull(proposalTtl, "proposalTtl");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized CommunityProposal create(String action, JsonNode input) {
        String safeAction = requireText(action, "action");
        JsonNode safeInput = Objects.requireNonNull(input, "input").deepCopy();
        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(proposalTtl);
        String id = UUID.randomUUID().toString();
        CommunityProposal proposal = new CommunityProposal(
                id,
                safeAction,
                safeInput,
                createdAt,
                expiresAt,
                CommunityProposalStatus.PENDING,
                sign(id, safeAction, safeInput, expiresAt),
                null,
                null
        );
        append(proposal);
        return proposal;
    }

    public synchronized List<CommunityProposal> list() {
        return List.copyOf(new ArrayList<>(latestState().values()));
    }

    public synchronized CommunityProposal claim(String id, String actor) {
        String safeId = requireText(id, "id");
        String safeActor = requireText(actor, "actor");
        CommunityProposal proposal = requireProposal(latestState(), safeId);
        if (proposal.status() != CommunityProposalStatus.PENDING) {
            throw new IllegalStateException("proposal " + safeId + " is not pending");
        }
        if (!proposal.expiresAt().isAfter(clock.instant())) {
            throw new IllegalStateException("proposal " + safeId + " is expired");
        }
        String expectedSignature = sign(
                proposal.id(),
                proposal.action(),
                proposal.input(),
                proposal.expiresAt()
        );
        if (!MessageDigest.isEqual(
                proposal.signature().getBytes(StandardCharsets.US_ASCII),
                expectedSignature.getBytes(StandardCharsets.US_ASCII)
        )) {
            throw new IllegalStateException("proposal " + safeId + " signature is invalid");
        }

        CommunityProposal claimed = new CommunityProposal(
                proposal.id(),
                proposal.action(),
                proposal.input(),
                proposal.createdAt(),
                proposal.expiresAt(),
                CommunityProposalStatus.CLAIMED,
                proposal.signature(),
                proposal.result(),
                safeActor
        );
        append(claimed);
        return claimed;
    }

    public synchronized CommunityProposal complete(
            String id,
            CommunityProposalStatus status,
            JsonNode result
    ) {
        if (status != CommunityProposalStatus.COMPLETED && status != CommunityProposalStatus.FAILED) {
            throw new IllegalArgumentException("terminal proposal status must be COMPLETED or FAILED");
        }
        CommunityProposal proposal = requireProposal(latestState(), requireText(id, "id"));
        if (proposal.status() != CommunityProposalStatus.CLAIMED) {
            throw new IllegalStateException("proposal " + proposal.id() + " is not claimed");
        }
        CommunityProposal completed = new CommunityProposal(
                proposal.id(),
                proposal.action(),
                proposal.input(),
                proposal.createdAt(),
                proposal.expiresAt(),
                status,
                proposal.signature(),
                Objects.requireNonNull(result, "result"),
                proposal.actor()
        );
        append(completed);
        return completed;
    }

    private Map<String, CommunityProposal> latestState() {
        if (!Files.exists(journalPath)) {
            return Map.of();
        }
        LinkedHashMap<String, CommunityProposal> latest = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(journalPath, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    CommunityProposal proposal = readProposal(objectMapper.readTree(line));
                    latest.put(proposal.id(), proposal);
                } catch (RuntimeException | IOException ignored) {
                    // Preserve the previous valid state for this proposal and fail closed on claim.
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read proposal journal: " + journalPath, exception);
        }
        return Map.copyOf(latest);
    }

    private CommunityProposal readProposal(JsonNode node) {
        JsonNode result = node.has("result") ? node.get("result") : null;
        String actor = node.hasNonNull("actor") ? node.get("actor").asText() : null;
        return new CommunityProposal(
                requiredText(node, "id"),
                requiredText(node, "action"),
                requiredNode(node, "input"),
                Instant.parse(requiredText(node, "createdAt")),
                Instant.parse(requiredText(node, "expiresAt")),
                CommunityProposalStatus.valueOf(requiredText(node, "status")),
                requiredText(node, "signature"),
                result,
                actor
        );
    }

    private void append(CommunityProposal proposal) {
        try {
            Path parent = journalPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    journalPath,
                    objectMapper.writeValueAsString(writeProposal(proposal)) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to append proposal journal: " + journalPath, exception);
        }
    }

    private ObjectNode writeProposal(CommunityProposal proposal) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", proposal.id());
        node.put("action", proposal.action());
        node.set("input", proposal.input());
        node.put("createdAt", proposal.createdAt().toString());
        node.put("expiresAt", proposal.expiresAt().toString());
        node.put("status", proposal.status().name());
        node.put("signature", proposal.signature());
        if (proposal.result() != null) {
            node.set("result", proposal.result());
        }
        if (proposal.actor() != null && !proposal.actor().isBlank()) {
            node.put("actor", proposal.actor());
        }
        return node;
    }

    private String sign(String id, String action, JsonNode input, Instant expiresAt) {
        ObjectNode signingPayload = objectMapper.createObjectNode();
        signingPayload.put("id", id);
        signingPayload.put("action", action);
        signingPayload.set("input", input);
        signingPayload.put("expiresAt", expiresAt.toString());
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingSecret, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(objectMapper.writeValueAsBytes(signingPayload));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException("Failed to sign Community Agent proposal.", exception);
        }
    }

    private static CommunityProposal requireProposal(
            Map<String, CommunityProposal> proposals,
            String id
    ) {
        CommunityProposal proposal = proposals.get(id);
        if (proposal == null) {
            throw new IllegalArgumentException("unknown proposal " + id);
        }
        return proposal;
    }

    private static JsonNode requiredNode(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static String requiredText(JsonNode node, String fieldName) {
        return requireText(node.path(fieldName).asText(""), fieldName);
    }

    private static String requireText(String value, String fieldName) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        throw new IllegalArgumentException(fieldName + " must not be blank");
    }
}
