package org.tavall.community.proposal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommunityProposalJournalTest {
    private static final Instant NOW = Instant.parse("2026-09-10T18:00:00Z");

    @TempDir
    Path tempDirectory;

    @Test
    void createsClaimsAndCompletesSignedProposalWithoutMutableRuntimeState() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        Path journalPath = tempDirectory.resolve("proposals.jsonl");
        CommunityProposalJournal journal = journal(objectMapper, journalPath, NOW);
        JsonNode input = objectMapper.createObjectNode()
                .put("channelId", "123")
                .put("content", "hello");

        CommunityProposal created = journal.create("send_message", input);
        CommunityProposal claimed = journal.claim(created.id(), "operator");
        CommunityProposal completed = journal.complete(
                created.id(),
                CommunityProposalStatus.COMPLETED,
                objectMapper.createObjectNode().put("messageId", "456")
        );

        assertThat(created.status()).isEqualTo(CommunityProposalStatus.PENDING);
        assertThat(claimed.status()).isEqualTo(CommunityProposalStatus.CLAIMED);
        assertThat(completed.status()).isEqualTo(CommunityProposalStatus.COMPLETED);
        assertThat(journal.list()).containsExactly(completed);
    }

    @Test
    void rejectsExpiredProposal() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        Path journalPath = tempDirectory.resolve("proposals.jsonl");
        CommunityProposalJournal creator = journal(objectMapper, journalPath, NOW);
        CommunityProposal proposal = creator.create(
                "send_message",
                objectMapper.createObjectNode().put("channelId", "123").put("content", "hello")
        );
        CommunityProposalJournal later = journal(objectMapper, journalPath, NOW.plus(Duration.ofMinutes(16)));

        assertThatThrownBy(() -> later.claim(proposal.id(), "operator"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rejectsTamperedSignatureBeforeClaim() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        Path journalPath = tempDirectory.resolve("proposals.jsonl");
        CommunityProposalJournal journal = journal(objectMapper, journalPath, NOW);
        CommunityProposal proposal = journal.create(
                "send_message",
                objectMapper.createObjectNode().put("channelId", "123").put("content", "hello")
        );

        String line = Files.readString(journalPath, StandardCharsets.UTF_8).trim();
        JsonNode node = objectMapper.readTree(line);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("signature", "00".repeat(32));
        Files.writeString(journalPath, objectMapper.writeValueAsString(node) + System.lineSeparator(), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> journal.claim(proposal.id(), "operator"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("signature is invalid");
    }

    @Test
    void keepsLastValidStateWhenJournalContainsCorruptLine() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        Path journalPath = tempDirectory.resolve("proposals.jsonl");
        CommunityProposalJournal journal = journal(objectMapper, journalPath, NOW);
        CommunityProposal proposal = journal.create(
                "send_message",
                objectMapper.createObjectNode().put("channelId", "123").put("content", "hello")
        );
        Files.writeString(
                journalPath,
                "{not-json" + System.lineSeparator(),
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND
        );

        assertThat(journal.list()).containsExactly(proposal);
    }

    private static CommunityProposalJournal journal(
            ObjectMapper objectMapper,
            Path journalPath,
            Instant instant
    ) {
        return new CommunityProposalJournal(
                objectMapper,
                journalPath,
                "test-signing-secret",
                Duration.ofMinutes(15),
                Clock.fixed(instant, ZoneOffset.UTC)
        );
    }
}
