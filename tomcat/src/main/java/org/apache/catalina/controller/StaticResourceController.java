package org.apache.catalina.controller;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.apache.coyote.http11.HttpRequest;
import org.apache.coyote.http11.HttpResponse;

public class StaticResourceController extends AbstractController {

    @Override
    public boolean supports(final HttpRequest request) {
        return true;
    }

    @Override
    protected void doGet(final HttpRequest request, final HttpResponse response) throws Exception {
        final String requestPath = request.getRequestLine().getPath();
        final String resourcePath = "static" + requestPath;
        final URL resource = getClass().getClassLoader().getResource(resourcePath);

        if (resource != null) {
            final byte[] body = Files.readAllBytes(Path.of(resource.toURI()));
            final String contentType = getContentType(resourcePath);
            final Map<String, String> headers = createDefaultHeaders(contentType, body.length);

            response.setStatusLine("HTTP/1.1 200 OK");
            response.setHeaders(headers);
            response.setBody(body);
        } else {
            handleNotFound(response);
        }
    }

    private void handleNotFound(final HttpResponse response) throws Exception {
        final URL resource404 = getClass().getClassLoader().getResource("static/404.html");
        final byte[] body;
        if (resource404 != null) {
            body = Files.readAllBytes(Path.of(resource404.toURI()));
        } else {
            body = "404 Not Found".getBytes(StandardCharsets.UTF_8);
        }
        final Map<String, String> headers = createDefaultHeaders("text/html;charset=utf-8", body.length);

        response.setStatusLine("HTTP/1.1 404 Not Found");
        response.setHeaders(headers);
        response.setBody(body);
    }

    private String getContentType(final String resourcePath) {
        if (resourcePath.endsWith(".css")) {
            return "text/css";
        }
        if (resourcePath.endsWith(".js")) {
            return "text/javascript";
        }
        return "text/html;charset=utf-8";
    }

    private Map<String, String> createDefaultHeaders(final String contentType, final int contentLength) {
        final Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", contentType);
        headers.put("Content-Length", String.valueOf(contentLength));
        return headers;
    }
}
