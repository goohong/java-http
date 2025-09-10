package org.apache.coyote.http11;

import com.techcourse.db.InMemoryUserRepository;
import com.techcourse.exception.UncheckedServletException;
import com.techcourse.model.User;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
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

            final String requestLine = bufferedReader.readLine();
            if (requestLine == null) {
                return;
            }

            final String[] requestLineParts = requestLine.split(" ");
            final String requestHttpMethod = requestLineParts[0];
            final String fullRequestURI = requestLineParts[1];
            final Map<String, String> requestHeaders = parseRequestHeaders(bufferedReader);
            final Map<String, String> requestCookies = parseCookies(requestHeaders.get("Cookie"));

            final URI uri = new URI(fullRequestURI);
            final String requestPath = uri.getPath();
            final Map<String, String> requestParams = parseRequestParams(uri, requestHttpMethod, requestHeaders,
                    bufferedReader);

            final HttpResponse response = route(requestHttpMethod, requestPath, requestParams, requestCookies);

            final StringBuilder headerBuilder = new StringBuilder();
            for (Entry<String, String> entry : response.headers().entrySet()) {
                headerBuilder.append(entry.getKey())
                        .append(": ")
                        .append(entry.getValue())
                        .append(" \r\n");
            }

            outputStream.write(("HTTP/1.1 " + response.statusCode() + " \r\n").getBytes(StandardCharsets.UTF_8));
            outputStream.write(headerBuilder.toString().getBytes(StandardCharsets.UTF_8));
            outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
            outputStream.write(response.body());
            outputStream.flush();

        } catch (IOException | URISyntaxException | UncheckedServletException e) {
            log.atError().log(e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            log.atWarn().log(e.getMessage());
        }
    }

    private Map<String, String> parseRequestParams(final URI uri, final String httpMethod,
                                                   final Map<String, String> requestHeaders,
                                                   final BufferedReader bufferedReader) throws IOException {
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

    private HttpResponse route(final String httpMethod,
                               final String requestPath,
                               final Map<String, String> requestParams,
                               final Map<String, String> cookies)
            throws IOException, URISyntaxException {
        if (httpMethod.equals("GET") && requestPath.equals("/")) {
            byte[] body = "Hello world!".getBytes();
            return new HttpResponse(
                    "200 OK",
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
            return new HttpResponse("302 Found", headers, new byte[0]);
        }

        final Map<String, String> headers = new HashMap<>();
        if (sessionId != null) {
            log.atInfo().log("만료되었거나 유효하지 않은 세션입니다. JSESSIONID: {}", sessionId);
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

            log.atInfo().log("로그인 성공. user: {}", user.get());
            log.atInfo().log("세션 생성. JSESSIONID: {}; Max-Age=3600", session.getId());
            SessionManager.getInstance().add(session);
            final byte[] body = "로그인 성공".getBytes(StandardCharsets.UTF_8);
            final Map<String, String> headers = createDefaultHeaders("text/html;charset=utf-8", body.length);
            headers.put("Location", "/");
            headers.put("Set-Cookie", "JSESSIONID=" + session.getId());
            return new HttpResponse("302 Found", headers, body);
        }

        log.atInfo().log("로그인 실패");
        final URL resource = getClass().getClassLoader().getResource("static/401.html");
        final byte[] body = Files.readAllBytes(Path.of(resource.toURI()));
        final Map<String, String> headers = createDefaultHeaders("text/html;charset=utf-8", body.length);
        return new HttpResponse("401 Unauthorized", headers, body);
    }

    private HttpResponse handleRegisterPost(final Map<String, String> requestParams) {
        final String account = requestParams.get("account");
        final String email = requestParams.get("email");
        final String password = requestParams.get("password");
        final Optional<User> user = InMemoryUserRepository.findByAccount(account);

        if (user.isPresent()) {
            log.atInfo().log("회원가입 실패 - 계정 중복: {}", account);
            final Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Location", "/register");
            return new HttpResponse("302 Found", headers, new byte[0]);
        }

        InMemoryUserRepository.save(new User(account, password, email));
        log.atInfo().log("회원가입 성공. user account: {}", account);

        final Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Location", "/login");
        return new HttpResponse("302 Found", headers, new byte[0]);
    }

    private HttpResponse handleStaticResource(final String requestPath) throws IOException, URISyntaxException {
        final String resourcePath = "static" + requestPath;
        final URL resource = getClass().getClassLoader().getResource(resourcePath);
        if (resource != null) {
            final byte[] body = Files.readAllBytes(Path.of(resource.toURI()));
            final String contentType = getContentType(resourcePath);
            final Map<String, String> headers = createDefaultHeaders(contentType, body.length);
            return new HttpResponse("200 OK", headers, body);
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
        return new HttpResponse("404 Not Found", headers, body);
    }

    private HttpResponse serveStaticHtml(final String resourcePath) throws IOException, URISyntaxException {
        final URL resource = getClass().getClassLoader().getResource(resourcePath);
        if (resource != null) {
            final byte[] body = Files.readAllBytes(Path.of(resource.toURI()));
            final Map<String, String> headers = createDefaultHeaders("text/html;charset=utf-8", body.length);
            return new HttpResponse("200 OK", headers, body);
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
            return new HttpResponse("200 OK", headers, body);
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

    private Map<String, String> parseRequestHeaders(final BufferedReader bufferedReader) throws IOException {
        Map<String, String> requestHeaders = new HashMap<>();
        String line;
        while ((line = bufferedReader.readLine()) != null && !line.isEmpty()) {
            String[] headerParts = line.split(": ", 2);
            if (headerParts.length == 2) {
                requestHeaders.put(headerParts[0], headerParts[1]);
            }
        }
        return requestHeaders;
    }
}
