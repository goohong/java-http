package org.apache.catalina.controller;

import com.techcourse.db.InMemoryUserRepository;
import com.techcourse.model.User;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.catalina.session.Session;
import org.apache.catalina.session.SessionManager;
import org.apache.coyote.http11.HttpRequest;
import org.apache.coyote.http11.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginController extends AbstractController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    @Override
    public boolean supports(final HttpRequest request) {
        return request.getRequestLine().getPath().equals("/login");
    }

    @Override
    protected void doPost(final HttpRequest request, final HttpResponse response) throws Exception {
        Map<String, String> requestParams = request.getRequestParams();
        Map<String, String> cookies = request.getHttpCookies();

        final String account = requestParams.get("account");
        final String password = requestParams.get("password");
        final Optional<User> user = InMemoryUserRepository.findByAccount(account);

        if (user.isPresent() && user.get().checkPassword(password) && !cookies.containsKey("JSESSIONID")) {
            Session session = new Session(UUID.randomUUID().toString());
            session.setAttribute("user", user);

            log.atInfo().log("Login success. user: {}", user.get());
            log.atInfo().log("Session created. JSESSIONID: {}", session.getId());
            SessionManager.getInstance().add(session);

            final byte[] body = "Login success".getBytes(StandardCharsets.UTF_8);

            final Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "text/html;charset=utf-8");
            headers.put("Content-Length", String.valueOf(body.length));
            headers.put("Location", "/index.html");
            headers.put("Set-Cookie", "JSESSIONID=" + session.getId() + "; Max-Age=3600");

            response.setStatusLine("HTTP/1.1 302 Found");
            response.setHeaders(headers);
            response.setBody(body);
        } else {
            log.atInfo().log("Login failed");
            final URL resource = getClass().getClassLoader().getResource("static/401.html");
            final byte[] body = Files.readAllBytes(Path.of(resource.toURI()));
            final Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "text/html;charset=utf-8");
            headers.put("Content-Length", String.valueOf(body.length));
            response.setStatusLine("HTTP/1.1 401 Unauthorized");
            response.setHeaders(headers);
            response.setBody(body);
        }
    }

    @Override
    protected void doGet(final HttpRequest request, final HttpResponse response) throws Exception {
        final String sessionId = request.getHttpCookies().get("JSESSIONID");

        if (sessionId != null && SessionManager.getInstance().findSession(sessionId) != null) {
            final Map<String, String> headers = new HashMap<>();
            final byte[] body = new byte[0];
            headers.put("Location", "/index.html");
            headers.put("Content-Type", "text/html;charset=utf-8");
            headers.put("Content-Length", String.valueOf(body.length));

            response.setStatusLine("HTTP/1.1 302 Found");
            response.setHeaders(headers);
            response.setBody(body);
        } else {

            final Map<String, String> headers = new HashMap<>();
            if (sessionId != null) {
                log.atInfo().log("Expired or invalid session. JSESSIONID: {}", sessionId);
                headers.put("Set-Cookie", "JSESSIONID=; Max-Age=0;");
            }
            final URL resource = getClass().getClassLoader().getResource("static/login.html");
            if (resource != null) {
                final byte[] body = Files.readAllBytes(Path.of(resource.toURI()));
                headers.put("Content-Type", "text/html;charset=utf-8");
                headers.put("Content-Length", String.valueOf(body.length));

                response.setStatusLine("HTTP/1.1 200 OK");
                response.setHeaders(headers);
                response.setBody(body);
            }
        }
    }
}
