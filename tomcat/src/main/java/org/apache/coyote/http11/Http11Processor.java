package org.apache.coyote.http11;

import com.techcourse.db.InMemoryUserRepository;
import com.techcourse.exception.UncheckedServletException;
import com.techcourse.model.User;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import org.apache.coyote.Processor;
import org.apache.coyote.http11.session.Session;
import org.apache.coyote.http11.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Http11Processor implements Runnable, Processor {

    private static final Logger log = LoggerFactory.getLogger(Http11Processor.class);

    private final Socket connection;

    public Http11Processor(final Socket connection) {
        this.connection = connection;
    }

    @Override
    public void run() {
        log.atInfo().log("connect host: {}, port: {}", connection.getInetAddress(), connection.getPort());
        process(connection);
    }

    @Override
    public void process(final Socket connection) {
        try (final var inputStream = connection.getInputStream();
             final var inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             final var bufferedReader = new BufferedReader(inputStreamReader);
             final var outputStream = connection.getOutputStream()) {

            final HttpRequest httpRequest = new HttpRequest(bufferedReader);

            final String requestHttpMethod = httpRequest.getRequestLine().getMethod();
            final String requestPath = httpRequest.getRequestLine().getPath();
            final Map<String, String> requestParams = httpRequest.getRequestParams();
            final Map<String, String> requestCookies = httpRequest.getHttpCookies();

            final HttpResponse response = route(requestHttpMethod, requestPath, requestParams, requestCookies);

            outputStream.write((response.getStatusLine().toString() + " \r\n").getBytes(StandardCharsets.UTF_8));

            final StringBuilder headerBuilder = new StringBuilder();
            for (Entry<String, String> entry : response.getHeaders().entrySet()) {
                headerBuilder.append(entry.getKey())
                        .append(": ")
                        .append(entry.getValue())
                        .append(" \r\n");
            }

            outputStream.write(headerBuilder.toString().getBytes(StandardCharsets.UTF_8));
            outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
            outputStream.write(response.getBody());
            outputStream.flush();

        } catch (IOException e) {
            // This can happen if the client closes the connection or sends an empty request
            log.atInfo().log("Connection error: {}", e.getMessage());
        } catch (URISyntaxException | UncheckedServletException e) {
            log.atError().log(e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            // This can happen for malformed request lines
            log.atWarn().log(e.getMessage());
        }
    }

    private HttpResponse route(final String httpMethod,
                               final String requestPath,
                               final Map<String, String> requestParams,
                               final Map<String, String> cookies)
            throws IOException, URISyntaxException {
        if (httpMethod.equals("GET") && requestPath.equals("/")) {
            byte[] body = "Hello world!".getBytes();
            return new HttpResponse(
                    "HTTP/1.1 200 OK",
                    createDefaultHeaders("text/html;charset=utf-8", body.length),
                    body
            );
        }
        if (httpMethod.equals("GET") && requestPath.equals("/login")) {
            return handleLoginGet(cookies);
        }
        if (httpMethod.equals("POST") && requestPath.equals("/login")) {
            return handleLoginPost(requestParams, cookies);
        }
        if (httpMethod.equals("GET") && requestPath.equals("/register")) {
            return serveStaticHtml("static/register.html");
        }
        if (httpMethod.equals("POST") && requestPath.equals("/register")) {
            return handleRegisterPost(requestParams);
        }
        return handleStaticResource(requestPath);
    }

    private HttpResponse handleLoginGet(final Map<String, String> cookies) throws IOException, URISyntaxException {
        final String sessionId = cookies.get("JSESSIONID");

        if (sessionId != null && SessionManager.getInstance().findSession(sessionId) != null) {
            final Map<String, String> headers = new HashMap<>();
            headers.put("Location", "/");
            return new HttpResponse("HTTP/1.1 302 Found", headers, new byte[0]);
        }

        final Map<String, String> headers = new HashMap<>();
        if (sessionId != null) {
            log.atInfo().log("Expired or invalid session. JSESSIONID: {}", sessionId);
            headers.put("Set-Cookie", "JSESSIONID=; Max-Age=0;");
        }

        return serveStaticHtml("static/login.html", headers);
    }

    private HttpResponse handleLoginPost(final Map<String, String> requestParams, final Map<String, String> cookies)
            throws IOException, URISyntaxException {
        final String account = requestParams.get("account");
        final String password = requestParams.get("password");
        final Optional<User> user = InMemoryUserRepository.findByAccount(account);

        if (user.isPresent() && user.get().checkPassword(password) && !cookies.containsKey("JSESSIONID")) {
            Session session = new Session(UUID.randomUUID().toString());
            session.setAttribute("user", user);

            log.atInfo().log("Login success. user: {}", user.get());
            log.atInfo().log("Session created. JSESSIONID: {}; Max-Age=3600", session.getId());
            SessionManager.getInstance().add(session);
            final byte[] body = "Login success".getBytes(StandardCharsets.UTF_8);
            final Map<String, String> headers = createDefaultHeaders("text/html;charset=utf-8", body.length);
            headers.put("Location", "/");
            headers.put("Set-Cookie", "JSESSIONID=" + session.getId());
            return new HttpResponse("HTTP/1.1 302 Found", headers, body);
        }

        log.atInfo().log("Login failed");
        final URL resource = getClass().getClassLoader().getResource("static/401.html");
        final byte[] body = Files.readAllBytes(Path.of(resource.toURI()));
        final Map<String, String> headers = createDefaultHeaders("text/html;charset=utf-8", body.length);
        return new HttpResponse("HTTP/1.1 401 Unauthorized", headers, body);
    }

    private HttpResponse handleRegisterPost(final Map<String, String> requestParams) {
        final String account = requestParams.get("account");
        final String email = requestParams.get("email");
        final String password = requestParams.get("password");
        final Optional<User> user = InMemoryUserRepository.findByAccount(account);

        if (user.isPresent()) {
            log.atInfo().log("Registration failed - account exists: {}", account);
            final Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Location", "/register");
            return new HttpResponse("HTTP/1.1 302 Found", headers, new byte[0]);
        }

        InMemoryUserRepository.save(new User(account, password, email));
        log.atInfo().log("Registration success. user account: {}", account);

        final Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Location", "/login");
        return new HttpResponse("HTTP/1.1 302 Found", headers, new byte[0]);
    }

    private HttpResponse handleStaticResource(final String requestPath) throws IOException, URISyntaxException {
        final String resourcePath = "static" + requestPath;
        final URL resource = getClass().getClassLoader().getResource(resourcePath);
        if (resource != null) {
            final byte[] body = Files.readAllBytes(Path.of(resource.toURI()));
            final String contentType = getContentType(resourcePath);
            final Map<String, String> headers = createDefaultHeaders(contentType, body.length);
            return new HttpResponse("HTTP/1.1 200 OK", headers, body);
        }

        return handleNotFound();
    }

    private HttpResponse handleNotFound() throws IOException, URISyntaxException {
        final URL resource404 = getClass().getClassLoader().getResource("static/404.html");
        final byte[] body;
        if (resource404 != null) {
            body = Files.readAllBytes(Path.of(resource404.toURI()));
        } else {
            body = "404 Not Found".getBytes(StandardCharsets.UTF_8);
        }
        final Map<String, String> headers = createDefaultHeaders("text/html;charset=utf-8", body.length);
        return new HttpResponse("HTTP/1.1 404 Not Found", headers, body);
    }

    private HttpResponse serveStaticHtml(final String resourcePath) throws IOException, URISyntaxException {
        final URL resource = getClass().getClassLoader().getResource(resourcePath);
        if (resource != null) {
            final byte[] body = Files.readAllBytes(Path.of(resource.toURI()));
            final Map<String, String> headers = createDefaultHeaders("text/html;charset=utf-8", body.length);
            return new HttpResponse("HTTP/1.1 200 OK", headers, body);
        }
        return handleNotFound();
    }


    private HttpResponse serveStaticHtml(final String resourcePath, final Map<String, String> headers)
            throws IOException, URISyntaxException {
        final URL resource = getClass().getClassLoader().getResource(resourcePath);
        if (resource != null) {
            final byte[] body = Files.readAllBytes(Path.of(resource.toURI()));
            headers.put("Content-Type", "text/html;charset=utf-8");
            headers.put("Content-Length", String.valueOf(body.length));
            return new HttpResponse("HTTP/1.1 200 OK", headers, body);
        }
        return handleNotFound();
    }

    private String getContentType(final String resourcePath) {
        if (resourcePath.endsWith(".css")) {
            return "text/css";
        }
        if (resourcePath.endsWith(".js")) {
            return "application/javascript";
        }
        return "text/html;charset=utf-8";
    }

    private Map<String, String> createDefaultHeaders(final String contentType, final int contentLength) {
        final Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", contentType);
        headers.put("Content-Length", String.valueOf(contentLength));
        return headers;
    }
}

