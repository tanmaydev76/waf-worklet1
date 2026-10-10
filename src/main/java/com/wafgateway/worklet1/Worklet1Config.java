package com.wafgateway.worklet1;

import com.wafgateway.worklet1.signature.Severity;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Worklet 1's configuration: where the rules live, resource limits, the
 * session cookie name, the DFA state cap, and the two policy knobs
 * ({@code block.minSeverity}, {@code body.nonJson}).
 *
 * <p>Loadable from a {@code .properties} file (classpath resource or
 * filesystem path — standard Java {@link Properties} format, so unlike
 * {@code rules.txt} there's no need for hand-written parsing here), or
 * constructed directly in code via the canonical constructor for tests.
 *
 * @param rulesFile         classpath resource or file path for {@code rules.txt}
 * @param maxBodyBytes      request bodies larger than this are blocked
 *                          before any scanning happens
 * @param maxTokens         JSON bodies producing more tokens than this are blocked
 * @param sessionCookieName which cookie holds the session id
 * @param dfaMaxStates      the state limit enforced on every compiled DFA
 * @param blockMinSeverity  the minimum severity a signature match must reach
 *                          to actually block the request
 * @param bodyNonJson       what to do with a non-empty, non-JSON body
 */
public record Worklet1Config(
        String rulesFile,
        int maxBodyBytes,
        int maxTokens,
        String sessionCookieName,
        int dfaMaxStates,
        Severity blockMinSeverity,
        NonJsonPolicy bodyNonJson) {

    /**
     * What to do with a non-empty body whose Content-Type isn't {@code application/json}.
     */
    public enum NonJsonPolicy {
        /** Pass the request through with an empty token list. */
        ALLOW,
        /** Block the request with {@link BlockReason#UNSUPPORTED_CONTENT_TYPE}. */
        BLOCK
    }

    /**
     * Returns the built-in defaults, matching {@code worklet1.properties}.
     *
     * @return a config with every setting at its default value
     */
    public static Worklet1Config defaults() {
        return new Worklet1Config("rules.txt", 1_048_576, 10_000, "WAF_SID",
                10_000, Severity.MEDIUM, NonJsonPolicy.ALLOW);
    }

    /**
     * Loads configuration from the default resource name {@code worklet1.properties}.
     *
     * @return the loaded config
     * @throws ConfigLoadException if the resource can't be found/read, or a
     *                              value is invalid
     */
    public static Worklet1Config load() throws ConfigLoadException {
        return load("worklet1.properties");
    }

    /**
     * Loads configuration from a classpath resource, falling back to a
     * filesystem path if no such resource exists. Any property not present
     * falls back to its default value.
     *
     * @param source a classpath resource name or a file path
     * @return the loaded config
     * @throws ConfigLoadException if the source can't be found/read, or a
     *                              value is invalid
     */
    public static Worklet1Config load(String source) throws ConfigLoadException {
        Properties props = new Properties();
        try (InputStream in = openSource(source)) {
            props.load(in);
        } catch (IOException e) {
            throw new ConfigLoadException("Failed to read config from '" + source + "': " + e.getMessage(), e);
        }

        Worklet1Config defaults = defaults();
        try {
            String rulesFile = props.getProperty("rules.file", defaults.rulesFile());
            int maxBodyBytes = Integer.parseInt(
                    props.getProperty("limits.maxBodyBytes", String.valueOf(defaults.maxBodyBytes())));
            int maxTokens = Integer.parseInt(
                    props.getProperty("limits.maxTokens", String.valueOf(defaults.maxTokens())));
            String sessionCookieName = props.getProperty("session.cookieName", defaults.sessionCookieName());
            int dfaMaxStates = Integer.parseInt(
                    props.getProperty("dfa.maxStates", String.valueOf(defaults.dfaMaxStates())));
            Severity blockMinSeverity = Severity.valueOf(
                    props.getProperty("block.minSeverity", defaults.blockMinSeverity().name()));
            NonJsonPolicy bodyNonJson = NonJsonPolicy.valueOf(
                    props.getProperty("body.nonJson", defaults.bodyNonJson().name()));

            return new Worklet1Config(rulesFile, maxBodyBytes, maxTokens, sessionCookieName,
                    dfaMaxStates, blockMinSeverity, bodyNonJson);
        } catch (IllegalArgumentException e) {
            throw new ConfigLoadException("Invalid value in config '" + source + "': " + e.getMessage(), e);
        }
    }

    private static InputStream openSource(String source) throws ConfigLoadException {
        InputStream classpathStream = Worklet1Config.class.getClassLoader().getResourceAsStream(source);
        if (classpathStream != null) {
            return classpathStream;
        }
        try {
            return new FileInputStream(source);
        } catch (FileNotFoundException e) {
            throw new ConfigLoadException("Config source not found on classpath or filesystem: '" + source + "'");
        }
    }
}
