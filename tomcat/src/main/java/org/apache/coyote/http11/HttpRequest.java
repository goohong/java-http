package org.apache.coyote.http11;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HttpRequest {
    private final RequestLine requestLine;
    private final Map<String, String> httpHeaders;
    private final Map<String, String> httpCookies;
    private final Map<String, String> requestParams;

    public HttpRequest(BufferedReader br) throws IOException {
        String line = br.readLine();
        if (line == null) {
            throw new IOException("Empty request from client.");
        }
        this.requestLine = RequestLine.from(line);
        this.httpHeaders = parseHeaders(br);
        this.httpCookies = parseCookies(httpHeaders.get("Cookie"));
        this.requestParams = parseRequestParams(this.requestLine, this.httpHeaders, br);
    }

    public RequestLine getRequestLine() {
        return requestLine;
    }

    public Map<String, String> getHttpHeaders() {
        return httpHeaders;
    }

    public Map<String, String> getHttpCookies() {
        return httpCookies;
    }

    public Map<String, String> getRequestParams() {
        return requestParams;
    }

    private Map<String, String> parseHeaders(final BufferedReader br) throws IOException {
        Map<String, String> requestHeaders = new HashMap<>();
        String line;
        while ((line = br.readLine()) != null && !line.isEmpty()) {
            String[] headerParts = line.split(": ", 2);
            if (headerParts.length == 2) {
                requestHeaders.put(headerParts[0], headerParts[1]);
            }
        }
        return requestHeaders;
    }

    private Map<String, String> parseCookies(final String requestCookies) {
        Map<String, String> cookies = new HashMap<>();

        if (requestCookies == null || requestCookies.isEmpty()) {
            return cookies;
        }

        final String[] cookiePairs = requestCookies.split(";");

        for (String pair : cookiePairs) {
            String trimmedPair = pair.trim();
            final String[] keyValue = trimmedPair.split("=", 2);

            if (keyValue.length == 2) {
                final String key = keyValue[0];
                final String value = keyValue[1];

                cookies.put(key, value);
            }
        }
        return cookies;
    }

    private Map<String, String> parseRequestParams(
            RequestLine requestLine,
            final Map<String, String> requestHeaders,
            final BufferedReader bufferedReader
    ) throws IOException {
        final URI uri = URI.create(requestLine.getPath());
        final String httpMethod = requestLine.getMethod();
        final Map<String, String> requestParams = new HashMap<>();

        if (uri.getQuery() != null) {
            requestParams.putAll(parseUrlEncoded(uri.getQuery()));
        }

        if ("POST".equals(httpMethod)) {
            final int contentLength = Integer.parseInt(requestHeaders.getOrDefault("Content-Length", "0"));
            if (contentLength > 0) {
                final String requestBody = parseRequestBody(contentLength, bufferedReader);
                requestParams.putAll(parseUrlEncoded(requestBody));
            }
        }
        return requestParams;
    }

    private String parseRequestBody(final int contentLength, final BufferedReader bufferedReader) throws IOException {
        char[] buffer = new char[contentLength];
        int bytesRead = bufferedReader.read(buffer, 0, contentLength);
        return new String(buffer, 0, bytesRead);
    }

    private Map<String, String> parseUrlEncoded(String encodedString) {
        Map<String, String> params = new HashMap<>();
        if (encodedString == null || encodedString.isEmpty()) {
            return params;
        }

        String[] pairs = encodedString.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        return params;
    }
}
