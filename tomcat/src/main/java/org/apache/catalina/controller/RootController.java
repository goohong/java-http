package org.apache.catalina.controller;

import java.util.HashMap;
import java.util.Map;
import org.apache.coyote.http11.HttpRequest;
import org.apache.coyote.http11.HttpResponse;

public class RootController extends AbstractController {

    @Override
    public boolean supports(final HttpRequest request) {
        return request.getRequestLine().getMethod().equals("GET") &&
                request.getRequestLine().getPath().equals("/");
    }

    @Override
    protected void doGet(final HttpRequest request, final HttpResponse response) {
        byte[] body = "Hello world!".getBytes();
        final Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/html;charset=utf-8");
        headers.put("Content-Length", String.valueOf(body.length));

        response.setStatusLine("HTTP/1.1 200 OK");
        response.setHeaders(headers);
        response.setBody(body);
    }
}
