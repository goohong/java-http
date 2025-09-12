package org.apache.catalina.controller;

import com.techcourse.db.InMemoryUserRepository;
import com.techcourse.model.User;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.coyote.http11.HttpRequest;
import org.apache.coyote.http11.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegisterController extends AbstractController {

    private static final Logger log = LoggerFactory.getLogger(RegisterController.class);

    @Override
    public boolean supports(final HttpRequest request) {
        return request.getRequestLine().getPath().equals("/register");
    }

    @Override
    protected void doGet(final HttpRequest request, final HttpResponse response) throws Exception {
        final URL resource = getClass().getClassLoader().getResource("static/register.html");
        if (resource != null) {
            final byte[] body = Files.readAllBytes(Path.of(resource.toURI()));
            final Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "text/html;charset=utf-8");
            headers.put("Content-Length", String.valueOf(body.length));

            response.setStatusLine("HTTP/1.1 200 OK");
            response.setHeaders(headers);
            response.setBody(body);
        }
    }

    @Override
    protected void doPost(final HttpRequest request, final HttpResponse response) throws Exception {
        final Map<String, String> requestParams = request.getRequestParams();
        final String account = requestParams.get("account");
        final String email = requestParams.get("email");
        final String password = requestParams.get("password");
        final Optional<User> user = InMemoryUserRepository.findByAccount(account);

        final Map<String, String> headers = new LinkedHashMap<>();
        if (user.isPresent()) {
            log.atInfo().log("회원가입 실패 - 계정 중복: {}", account);
            headers.put("Location", "/register");
        } else {
            InMemoryUserRepository.save(new User(account, password, email));
            log.atInfo().log("회원가입 성공. user account: {}", account);
            headers.put("Location", "/login");
        }

        response.setStatusLine("HTTP/1.1 302 Found");
        response.setHeaders(headers);
        response.setBody(new byte[0]);
    }
}
