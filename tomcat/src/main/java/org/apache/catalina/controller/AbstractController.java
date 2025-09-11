package org.apache.catalina.controller;

import org.apache.coyote.http11.HttpRequest;
import org.apache.coyote.http11.HttpResponse;

public abstract class AbstractController implements Controller {

    @Override
    public boolean supports(final HttpRequest request) {
        return request != null;
    }

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        if (request.getRequestLine().getMethod().equals("GET")) {
            doGet(request, response);
        } else if (request.getRequestLine().getMethod().equals("POST")) {
            doPost(request, response);
        } else {
            throw new RuntimeException("Undefined http method: " + request.getRequestLine().getMethod());
        }
    }

    protected void doPost(HttpRequest request, HttpResponse response) throws Exception { /* NOOP */ }

    protected void doGet(HttpRequest request, HttpResponse response) throws Exception { /* NOOP */ }
}
