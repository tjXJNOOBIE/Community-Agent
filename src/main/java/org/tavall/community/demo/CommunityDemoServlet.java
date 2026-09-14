package org.tavall.community.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.tavall.community.discord.DiscordGateway;
import org.tavall.community.handler.CommunityObservationHandler;
import org.tavall.community.handler.CommunityOperatorHandler;
import org.tavall.community.handler.CommunityProposalHandler;
import org.tavall.community.proposal.CommunityProposal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Loopback-only operator chat and proof surface for the real Discord development tenant. */
public final class CommunityDemoServlet extends HttpServlet {
    private static final String DEMO_RESOURCE = "/community/demo/index.html";

    private final ObjectMapper objectMapper;
    private final DiscordGateway discordGateway;
    private final String configuredGuildId;
    private final String configuredChannelId;
    private final String configuredChannelName;
    private final String document;
    private final List<Message> messages = new ArrayList<>();
    private final List<Operation> operations = new ArrayList<>();
    private String phase = "READY";
    private String guildName = "Configured development guild";
    private String channelName;
    private CommunityProposal pendingProposal;
    private String messageId;
    private String threadId;
    private JsonNode verification;
    private String error;

    public CommunityDemoServlet(
            ObjectMapper objectMapper,
            DiscordGateway discordGateway,
            String configuredGuildId,
            String configuredChannelId,
            String configuredChannelName
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.discordGateway = discordGateway;
        this.configuredGuildId = requireText(configuredGuildId, "configuredGuildId");
        this.configuredChannelId = requireText(configuredChannelId, "configuredChannelId");
        this.configuredChannelName = requireText(configuredChannelName, "configuredChannelName");
        this.channelName = this.configuredChannelName;
        this.document = loadDocument();
        messages.add(new Message(
                "system",
                "Discord Manager",
                "Connected to the Java operator surface. Discord content is observation data; mutations require a signed proposal and explicit approval."
        ));
    }

    @Override
    protected synchronized void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();
        if ("/state".equals(path)) {
            writeJson(response, HttpServletResponse.SC_OK, state());
            return;
        }
        if (path != null && !path.isBlank() && !"/".equals(path)
                && !path.matches("/(conversation|proof|audit)")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/html; charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getWriter().write(document);
    }

    @Override
    protected synchronized void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();
        if (path == null || !path.startsWith("/action/")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String action = path.substring("/action/".length());
        try {
            perform(action);
            writeJson(response, HttpServletResponse.SC_OK, state());
        } catch (RuntimeException exception) {
            error = safeMessage(exception);
            phase = "FAILED";
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, state());
        }
    }

    private void perform(String action) {
        error = null;
        switch (action) {
            case "observe" -> observe();
            case "propose-send" -> proposeSend();
            case "approve" -> approve();
            case "propose-edit" -> proposeEdit();
            case "propose-reaction" -> proposeReaction();
            case "propose-thread" -> proposeThread();
            case "verify" -> verify();
            case "cleanup" -> cleanup();
            default -> throw new IllegalArgumentException("Unknown Community demo action: " + action);
        }
    }

    private void observe() {
        requireDiscord();
        phase = "OBSERVING";
        appendMessage("assistant", "Discord Manager", "I’m reading the configured guild as untrusted observation data before proposing any action.");
        JsonNode result = new CommunityObservationHandler().observe();
        if ("blocked".equals(result.path("status").asText())) {
            throw new IllegalStateException(result.path("reason").asText("Discord observation is blocked"));
        }
        guildName = result.path("guild").path("name").asText(guildName);
        JsonNode channels = result.path("channels");
        if (channels.isArray()) {
            for (JsonNode channel : channels) {
                if (configuredChannelId.equals(channel.path("id").asText())) {
                    channelName = channel.path("name").asText(configuredChannelName);
                    break;
                }
            }
        }
        phase = "OBSERVED";
        appendMessage("assistant", "Discord Manager", "Observed " + guildName + " and resolved #" + channelName + ". I can now prepare a bounded community proposal.");
        operation("community_observe", "Read guild and channel state", "COMPLETED", result);
    }

    private void proposeSend() {
        phase = "PROPOSING";
        appendMessage("assistant", "Discord Manager", "I’m forming a signed proposal to post a temporary playtest note. Nothing has changed in Discord yet.");
        pendingProposal = new CommunityProposalHandler().propose(
                "send_message",
                Map.of(
                        "channelId", configuredChannelId,
                        "content", "Tavall Community Agent demo · temporary playtest note · created through Java proposal approval."
                )
        );
        operation("send_message", "Post a temporary playtest note", "PENDING", pendingProposal);
        phase = "APPROVAL_REQUIRED";
        appendMessage("assistant", "Approval required", "The message is ready for operator approval. The proposal is signed and expiring.");
    }

    private void approve() {
        if (pendingProposal == null) {
            throw new IllegalStateException("No pending proposal is available");
        }
        phase = "APPROVING";
        appendMessage("operator", "Operator", "Approve proposal " + pendingProposal.id());
        CommunityProposal completed = new CommunityOperatorHandler().approve(pendingProposal.id());
        pendingProposal = completed;
        Operation current = currentOperation();
        current.status = completed.status().name();
        current.result = completed.result();
        if (completed.result() != null) {
            String returnedMessageId = completed.result().path("id").asText("");
            if (!returnedMessageId.isBlank()) {
                if ("create_thread".equals(current.action)) {
                    threadId = returnedMessageId;
                } else {
                    messageId = returnedMessageId;
                    current.messageId = returnedMessageId;
                }
            }
        }
        phase = "COMPLETED".equals(completed.status().name()) ? "DISCORD_CHANGED" : "FAILED";
        appendMessage(
                "assistant",
                "Discord Manager",
                phase.equals("DISCORD_CHANGED")
                        ? current.label + " completed through the Discord API."
                        : "The proposal finished with status " + completed.status().name() + "."
        );
    }

    private void proposeEdit() {
        requireMessage();
        phase = "PROPOSING";
        pendingProposal = new CommunityProposalHandler().propose(
                "edit_message",
                Map.of(
                        "channelId", configuredChannelId,
                        "messageId", messageId,
                        "content", "Tavall Community Agent demo · updated playtest note · edited through Java approval."
                )
        );
        operation("edit_message", "Edit the bot-owned message", "PENDING", pendingProposal);
        phase = "APPROVAL_REQUIRED";
        appendMessage("assistant", "Approval required", "The bot-owned message edit is ready. The edit path verifies authorship before changing content.");
    }

    private void proposeReaction() {
        requireMessage();
        phase = "PROPOSING";
        pendingProposal = new CommunityProposalHandler().propose(
                "add_reaction",
                Map.of("channelId", configuredChannelId, "messageId", messageId, "emoji", "✅")
        );
        operation("add_reaction", "Add a community reaction", "PENDING", pendingProposal);
        phase = "APPROVAL_REQUIRED";
        appendMessage("assistant", "Approval required", "The reaction is ready. Discord will reflect it only after approval.");
    }

    private void proposeThread() {
        requireMessage();
        phase = "PROPOSING";
        pendingProposal = new CommunityProposalHandler().propose(
                "create_thread",
                Map.of("channelId", configuredChannelId, "messageId", messageId, "name", "community-agent-demo")
        );
        operation("create_thread", "Open a temporary message thread", "PENDING", pendingProposal);
        phase = "APPROVAL_REQUIRED";
        appendMessage("assistant", "Approval required", "The temporary message thread is ready for approval.");
    }

    private void verify() {
        requireMessage();
        phase = "VERIFYING";
        JsonNode message = requireDiscord().readMessage(configuredChannelId, messageId);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("messageId", message.path("id").asText(messageId));
        result.put("content", message.path("content").asText(""));
        result.put("author", message.path("author").path("username").asText(message.path("author").path("id").asText("unknown")));
        ArrayNode reactions = result.putArray("reactions");
        JsonNode sourceReactions = message.path("reactions");
        if (sourceReactions.isArray()) {
            for (JsonNode reaction : sourceReactions) {
                ObjectNode item = reactions.addObject();
                item.put("emoji", reaction.path("emoji").path("name").asText(reaction.path("emoji").path("id").asText("custom")));
                item.put("count", reaction.path("count").asInt(0));
            }
        }
        if (threadId == null || threadId.isBlank()) {
            Operation thread = findOperation("create_thread");
            if (thread != null && thread.result != null) {
                threadId = thread.result.path("id").asText("");
            }
        }
        result.put("threadId", threadId == null ? "" : threadId);
        result.put("source", "Discord REST API v10 read-back through Java gateway");
        verification = result;
        phase = "VERIFIED";
        appendMessage("assistant", "Verified from Discord", "Discord returned the bot message with its edited content and current reaction state.");
        operation("discord_read_back", "Read back the resulting Discord state", "COMPLETED", result);
    }

    private void cleanup() {
        requireMessage();
        phase = "CLEANING_UP";
        DiscordGateway gateway = requireDiscord();
        if (threadId != null && !threadId.isBlank()) {
            gateway.deleteThread(threadId);
        }
        gateway.deleteMessage(configuredChannelId, messageId);
        phase = "CLEANED";
        appendMessage("assistant", "Discord Manager", "Temporary demo message and thread removed from the development guild.");
        operation("cleanup", "Remove temporary demo state", "COMPLETED", null);
    }

    private DiscordGateway requireDiscord() {
        if (discordGateway == null) {
            throw new IllegalStateException("Discord bot token is not configured");
        }
        return discordGateway;
    }

    private void requireMessage() {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalStateException("Approve the temporary message proposal first");
        }
    }

    private Operation operation(String action, String label, String status, Object result) {
        Operation operation = new Operation(action, label, status);
        if (result instanceof CommunityProposal proposal) {
            operation.proposalId = proposal.id();
            operation.result = proposal.result();
        } else if (result instanceof JsonNode node) {
            operation.result = node.deepCopy();
        }
        operations.add(operation);
        return operation;
    }

    private Operation currentOperation() {
        if (operations.isEmpty()) {
            throw new IllegalStateException("No operation is active");
        }
        return operations.getLast();
    }

    private Operation findOperation(String action) {
        for (int index = operations.size() - 1; index >= 0; index--) {
            if (operations.get(index).action.equals(action)) {
                return operations.get(index);
            }
        }
        return null;
    }

    private void appendMessage(String role, String label, String text) {
        messages.add(new Message(role, label, text));
    }

    private ObjectNode state() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("phase", phase);
        root.put("guildId", configuredGuildId);
        root.put("guildName", guildName);
        root.put("channelId", configuredChannelId);
        root.put("channelName", channelName);
        root.put("messageId", messageId == null ? "" : messageId);
        root.put("threadId", threadId == null ? "" : threadId);
        if (error != null) {
            root.put("error", error);
        }

        ArrayNode messageArray = root.putArray("messages");
        for (Message message : messages) {
            ObjectNode item = messageArray.addObject();
            item.put("role", message.role);
            item.put("label", message.label);
            item.put("text", message.text);
        }

        ArrayNode operationArray = root.putArray("operations");
        for (Operation operation : operations) {
            ObjectNode item = operationArray.addObject();
            item.put("action", operation.action);
            item.put("label", operation.label);
            item.put("status", operation.status);
            if (operation.proposalId != null) item.put("proposalId", operation.proposalId);
            if (operation.messageId != null) item.put("messageId", operation.messageId);
            if (operation.result != null) item.set("result", operation.result.deepCopy());
        }

        if (pendingProposal != null) {
            ObjectNode proposal = root.putObject("proposal");
            proposal.put("id", pendingProposal.id());
            proposal.put("action", pendingProposal.action());
            proposal.put("status", pendingProposal.status().name());
            proposal.put("createdAt", pendingProposal.createdAt().toString());
            proposal.put("expiresAt", pendingProposal.expiresAt().toString());
            proposal.set("input", pendingProposal.input());
            if (pendingProposal.result() != null) proposal.set("result", pendingProposal.result());
        }
        if (verification != null) root.set("verification", verification.deepCopy());
        return root;
    }

    private void writeJson(HttpServletResponse response, int status, JsonNode value) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        objectMapper.writeValue(response.getWriter(), value);
    }

    private String loadDocument() {
        try (InputStream input = CommunityDemoServlet.class.getResourceAsStream(DEMO_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Community Agent demo resource is missing: " + DEMO_RESOURCE);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load Community Agent demo resource", exception);
        }
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    private static final class Message {
        private final String role;
        private final String label;
        private final String text;

        private Message(String role, String label, String text) {
            this.role = role;
            this.label = label;
            this.text = text;
        }
    }

    private static final class Operation {
        private final String action;
        private final String label;
        private String status;
        private String proposalId;
        private String messageId;
        private JsonNode result;

        private Operation(String action, String label, String status) {
            this.action = action;
            this.label = label;
            this.status = status;
        }
    }
}
