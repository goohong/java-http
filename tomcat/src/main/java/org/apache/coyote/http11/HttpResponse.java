package org.apache.coyote.http11;

import java.util.Map;

public final class HttpResponse {
    private final StatusLine statusLine;
    private final Map<String, String> headers;
    private final byte[] body;

    public HttpResponse(final String statusLineString, final Map<String, String> headers, final byte[] body) {
        this.statusLine = StatusLine.from(statusLineString);
        this.headers = headers;
        this.body = body;
    }

    public StatusLine getStatusLine() {
        return statusLine;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }
}
