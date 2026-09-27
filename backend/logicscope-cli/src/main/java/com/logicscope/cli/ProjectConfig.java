package com.logicscope.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

record ProjectConfig(Path projectRoot, int port) {
    static ProjectConfig load(Path configFile) throws IOException {
        String content = Files.readString(configFile);
        Integer port = null;
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("port:")) {
                String value = trimmed.substring("port:".length()).trim();
                if (value.isBlank()) {
                    throw new IllegalArgumentException("Invalid configuration: server.port is blank");
                }
                try {
                    port = Integer.parseInt(value);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Invalid configuration: server.port must be an integer, found '" + value + "'");
                }
                if (port < 1 || port > 65535) {
                    throw new IllegalArgumentException("Invalid configuration: server.port out of range: " + port);
                }
            }
        }
        if (port == null) {
            throw new IllegalArgumentException("Invalid configuration: missing server.port");
        }
        return new ProjectConfig(configFile.getParent().getParent(), port);
    }
}
