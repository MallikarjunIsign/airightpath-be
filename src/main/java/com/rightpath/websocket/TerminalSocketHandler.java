package com.rightpath.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * WebSocket handler for terminal-based code execution.
 *
 * Security features:
 * - Requires authenticated WebSocket connection (validated by WebSocketAuthInterceptor)
 * - Execution timeout to prevent resource exhaustion
 * - Per-user connection limits
 * - Input validation and logging
 */
@Component
public class TerminalSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TerminalSocketHandler.class);

    // Supported languages for code execution
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("java", "python", "c", "cpp");

    // Maximum script size (100KB)
    private static final int MAX_SCRIPT_SIZE = 100 * 1024;

    private final Map<String, Process> processMap = new ConcurrentHashMap<>();
    private final Map<String, BufferedWriter> writerMap = new ConcurrentHashMap<>();
    private final Map<String, ExecutorService> executorMap = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> timeoutMap = new ConcurrentHashMap<>();
    private final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    // Track active sessions per user to limit concurrent connections
    private final Map<String, Set<String>> userSessionsMap = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService timeoutScheduler = Executors.newScheduledThreadPool(2);

    @Value("${terminal.execution.timeout-seconds:60}")
    private int executionTimeoutSeconds = 60;

    @Value("${terminal.max-sessions-per-user:3}")
    private int maxSessionsPerUser = 3;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Get authenticated user from session attributes (set by WebSocketAuthInterceptor)
        String userEmail = (String) session.getAttributes().get(WebSocketAuthInterceptor.USER_EMAIL_ATTR);
        Boolean authenticated = (Boolean) session.getAttributes().get(WebSocketAuthInterceptor.AUTHENTICATED_ATTR);

        if (authenticated == null || !authenticated || userEmail == null) {
            log.warn("Unauthenticated WebSocket connection attempt rejected. SessionId={}", session.getId());
            closeSession(session, CloseStatus.POLICY_VIOLATION.withReason("Authentication required"));
            return;
        }

        // Check per-user session limit
        Set<String> userSessions = userSessionsMap.computeIfAbsent(userEmail, k -> ConcurrentHashMap.newKeySet());
        if (userSessions.size() >= maxSessionsPerUser) {
            log.warn("User {} exceeded max terminal sessions ({}). SessionId={}", userEmail, maxSessionsPerUser, session.getId());
            closeSession(session, CloseStatus.POLICY_VIOLATION.withReason("Maximum concurrent sessions exceeded"));
            return;
        }

        // Register session
        userSessions.add(session.getId());
        sessionUserMap.put(session.getId(), userEmail);
        sessionMap.put(session.getId(), session);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executorMap.put(session.getId(), executor);

        log.info("WebSocket terminal connection established. User={}, SessionId={}", userEmail, session.getId());
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        String userEmail = sessionUserMap.get(sessionId);

        if (userEmail == null) {
            log.warn("Message from unauthenticated session rejected. SessionId={}", sessionId);
            closeSession(session, CloseStatus.POLICY_VIOLATION.withReason("Not authenticated"));
            return;
        }

        String payload = message.getPayload();

        if (payload.startsWith("{")) {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String type = (String) data.get("type");

            if ("setup".equals(type)) {
                String language = (String) data.get("language");
                String script = (String) data.get("script");

                // Validate language
                if (language == null || !SUPPORTED_LANGUAGES.contains(language.toLowerCase())) {
                    sendErrorMessage(session, "Unsupported language: " + language);
                    log.warn("Unsupported language attempted. User={}, Language={}", userEmail, language);
                    return;
                }

                // Validate script size
                if (script == null || script.length() > MAX_SCRIPT_SIZE) {
                    sendErrorMessage(session, "Script too large (max " + (MAX_SCRIPT_SIZE / 1024) + "KB)");
                    log.warn("Script size exceeded. User={}, Size={}", userEmail, script != null ? script.length() : 0);
                    return;
                }

                log.info("Starting code execution. User={}, Language={}, ScriptSize={}", userEmail, language, script.length());
                startScriptExecution(session, language, script);
                return;

            } else if ("terminate".equals(type)) {
                log.info("Termination requested. User={}, SessionId={}", userEmail, sessionId);
                cleanupProcess(sessionId);
                return;
            }
        }

        // Handle stdin input
        BufferedWriter writer = writerMap.get(sessionId);
        Process process = processMap.get(sessionId);

        if (writer != null && process != null && process.isAlive()) {
            writer.write(payload);
            writer.newLine();
            writer.flush();
        }
    }

    private void startScriptExecution(WebSocketSession session, String language, String script) throws IOException {
        String sessionId = session.getId();
        cleanupProcess(sessionId); // Clean up any existing process

        File tempDir = Files.createTempDirectory("terminal").toFile();
        File scriptFile;
        List<String> command = new ArrayList<>();

        switch (language.toLowerCase()) {
            case "java":
                scriptFile = new File(tempDir, "Main.java");
                Files.write(scriptFile.toPath(), script.getBytes(StandardCharsets.UTF_8));
                Process compile = new ProcessBuilder("javac", "Main.java")
                        .directory(tempDir)
                        .redirectErrorStream(true)
                        .start();
                try {
                    boolean finished = compile.waitFor(30, TimeUnit.SECONDS);
                    if (!finished) {
                        compile.destroyForcibly();
                        sendErrorMessage(session, "Java compilation timed out");
                        return;
                    }
                    if (compile.exitValue() != 0) {
                        String error = new String(compile.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                        sendErrorMessage(session, "Java compilation failed:\n" + error);
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                command.add("java");
                command.add("Main");
                break;

            case "python":
                if (!isToolAvailable("python") && !isToolAvailable("python3")) {
                    sendErrorMessage(session, "Python not installed");
                    return;
                }
                scriptFile = new File(tempDir, "main.py");
                Files.write(scriptFile.toPath(), script.getBytes(StandardCharsets.UTF_8));
                command.add("python");
                command.add(scriptFile.getAbsolutePath());
                break;

            case "c":
                scriptFile = new File(tempDir, "main.c");
                Files.write(scriptFile.toPath(), script.getBytes(StandardCharsets.UTF_8));

                if (!isToolAvailable("gcc")) {
                    sendErrorMessage(session, "gcc compiler not found");
                    return;
                }

                String cExe = isWindows() ? "main.exe" : "main";
                Process gcc = new ProcessBuilder("gcc", "main.c", "-o", cExe)
                        .directory(tempDir)
                        .redirectErrorStream(true)
                        .start();

                try {
                    boolean finished = gcc.waitFor(30, TimeUnit.SECONDS);
                    if (!finished) {
                        gcc.destroyForcibly();
                        sendErrorMessage(session, "C compilation timed out");
                        return;
                    }
                    if (gcc.exitValue() != 0) {
                        String error = new String(gcc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                        sendErrorMessage(session, "C compilation failed:\n" + error);
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                command.add(getExecutablePath(tempDir, cExe));
                break;

            case "cpp":
                scriptFile = new File(tempDir, "main.cpp");
                Files.write(scriptFile.toPath(), script.getBytes(StandardCharsets.UTF_8));

                if (!isToolAvailable("g++")) {
                    sendErrorMessage(session, "g++ compiler not found");
                    return;
                }

                String cppExe = isWindows() ? "main.exe" : "main";
                Process gpp = new ProcessBuilder("g++", "main.cpp", "-o", cppExe)
                        .directory(tempDir)
                        .redirectErrorStream(true)
                        .start();

                try {
                    boolean finished = gpp.waitFor(30, TimeUnit.SECONDS);
                    if (!finished) {
                        gpp.destroyForcibly();
                        sendErrorMessage(session, "C++ compilation timed out");
                        return;
                    }
                    if (gpp.exitValue() != 0) {
                        String error = new String(gpp.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                        sendErrorMessage(session, "C++ compilation failed:\n" + error);
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                command.add(getExecutablePath(tempDir, cppExe));
                break;

            default:
                sendErrorMessage(session, "Unsupported language: " + language);
                return;
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(tempDir);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        processMap.put(sessionId, process);

        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        writerMap.put(sessionId, writer);

        // Schedule execution timeout
        ScheduledFuture<?> timeout = timeoutScheduler.schedule(() -> {
            String userEmailForTimeout = sessionUserMap.get(sessionId);
            log.warn("Execution timeout reached. User={}, SessionId={}", userEmailForTimeout, sessionId);
            sendMessage(session, "\r\n⚠️ Execution timed out after " + executionTimeoutSeconds + " seconds\r\n");
            cleanupProcess(sessionId);
        }, executionTimeoutSeconds, TimeUnit.SECONDS);
        timeoutMap.put(sessionId, timeout);

        // Output reader thread
        ExecutorService executor = executorMap.get(sessionId);
        if (executor != null) {
            executor.execute(() -> {
                try (InputStreamReader inputReader = new InputStreamReader(
                        process.getInputStream(), StandardCharsets.UTF_8)) {
                    char[] buffer = new char[1024];
                    int charsRead;
                    while ((charsRead = inputReader.read(buffer)) != -1 && !Thread.currentThread().isInterrupted()) {
                        String output = new String(buffer, 0, charsRead);
                        sendMessage(session, output);
                    }
                } catch (IOException e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        sendErrorMessage(session, "Output stream error: " + e.getMessage());
                    }
                }
            });

            // Process completion monitor thread
            executor.execute(() -> {
                try {
                    int exitCode = process.waitFor();
                    // Cancel timeout since process completed
                    ScheduledFuture<?> future = timeoutMap.remove(sessionId);
                    if (future != null) {
                        future.cancel(false);
                    }
                    sendMessage(session, "\r\nProcess exited with code: " + exitCode + "\r\n");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
    }

    private void cleanupProcess(String sessionId) {
        // Cancel timeout
        ScheduledFuture<?> timeout = timeoutMap.remove(sessionId);
        if (timeout != null) {
            timeout.cancel(false);
        }

        // Destroy process
        Process process = processMap.remove(sessionId);
        if (process != null) {
            process.destroyForcibly();
        }

        // Close writer
        BufferedWriter writer = writerMap.remove(sessionId);
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                log.debug("Error closing writer for session {}: {}", sessionId, e.getMessage());
            }
        }
    }

    private String getExecutablePath(File dir, String exeName) {
        return isWindows() ? new File(dir, exeName).getAbsolutePath() : "./" + exeName;
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private boolean isToolAvailable(String tool) {
        try {
            Process p = isWindows()
                    ? new ProcessBuilder("where", tool).start()
                    : new ProcessBuilder("which", tool).start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private void sendMessage(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(message));
                }
            }
        } catch (IOException e) {
            log.debug("Failed to send message to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private void sendErrorMessage(WebSocketSession session, String error) {
        sendMessage(session, "\r\n❌ ERROR: " + error + "\r\n");
    }

    private void closeSession(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            log.debug("Error closing session {}: {}", session.getId(), e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        String userEmail = sessionUserMap.remove(sessionId);

        cleanupProcess(sessionId);

        ExecutorService executor = executorMap.remove(sessionId);
        if (executor != null) {
            executor.shutdownNow();
        }

        sessionMap.remove(sessionId);

        // Remove from user's session set
        if (userEmail != null) {
            Set<String> userSessions = userSessionsMap.get(userEmail);
            if (userSessions != null) {
                userSessions.remove(sessionId);
                if (userSessions.isEmpty()) {
                    userSessionsMap.remove(userEmail);
                }
            }
        }

        log.info("WebSocket terminal connection closed. User={}, SessionId={}, Status={}",
                userEmail, sessionId, status);
    }
}
