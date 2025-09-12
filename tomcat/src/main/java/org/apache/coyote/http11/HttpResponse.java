package org.apache.coyote.http11;

import java.util.HashMap;
import java.util.Map;

public class HttpResponse {
    private StatusLine statusLine;
    private Map<String, String> headers;
    private byte[] body;

    public HttpResponse(final StatusLine statusLine, final Map<String, String> headers, final byte[] body) {
        this.statusLine = statusLine;
        this.headers = headers;
        this.body = body;
    }

    public static HttpResponse empty() {
        return new HttpResponse(StatusLine.empty(), new HashMap<>(), "".getBytes());
    }

    public StatusLine getStatusLine() {
        return statusLine;
    }

    public void setStatusLine(final String statusLine) {
        this.statusLine = StatusLine.from(statusLine);
    }

    public void setStatusLine(final StatusLine statusLine) {
        this.statusLine = statusLine;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(final Map<String, String> headers) {
        this.headers = headers;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(final byte[] body) {
        this.body = body;
    }
}
