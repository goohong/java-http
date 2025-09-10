package org.apache.coyote.http11;

public class StatusLine {
    private final String protocolVersion;
    private final String statusCode;
    private final String statusMessage;

    private StatusLine(final String protocolVersion, final String statusCode, final String statusMessage) {
        this.protocolVersion = protocolVersion;
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
    }

    public static StatusLine from(final String statusLine) {
        if (statusLine == null || statusLine.isEmpty()) {
            throw new IllegalArgumentException("Status line cannot be null or empty");
        }
        final String[] parts = statusLine.split(" ", 3);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid status line format: " + statusLine);
        }
        final String protocolVersion = parts[0];
        final String statusCode = parts[1];
        final String statusMessage = (parts.length > 2) ? parts[2] : "";
        return new StatusLine(protocolVersion, statusCode, statusMessage);
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    @Override
    public String toString() {
        return protocolVersion + " " + statusCode + " " + statusMessage;
    }
}
