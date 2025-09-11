package org.apache.catalina.controller;

import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.coyote.http11.HttpRequest;

public class RequestMapping {

    private final static Set<Controller> CONTROLLERS = new LinkedHashSet<>();
    private final static RequestMapping INSTANCE = new RequestMapping();

    public RequestMapping() {
        CONTROLLERS.add(new LoginController());
        CONTROLLERS.add(new RegisterController());
        CONTROLLERS.add(new RootController());
        CONTROLLERS.add(new StaticResourceController());
    }

    public static RequestMapping getInstance() {
        return INSTANCE;
    }

    public Controller getController(HttpRequest request) {
        return CONTROLLERS.stream()
                .filter(controller -> controller.supports(request))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Invalid request"));
    }
}
