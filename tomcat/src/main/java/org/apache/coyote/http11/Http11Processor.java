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
import java.util.Optional;
import org.apache.coyote.Processor;
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
        log.info("connect host: {}, port: {}", connection.getInetAddress(), connection.getPort());
        process(connection);
    }

    @Override
    public void process(final Socket connection) {
        try (final var inputStream = connection.getInputStream();
             final var inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             final var bufferedReader = new BufferedReader(inputStreamReader);
             final var outputStream = connection.getOutputStream()) {

            String requestLine = bufferedReader.readLine();
            if (requestLine == null) {
                return;
            }
            String fullRequestURI = requestLine.split(" ")[1];

            URI uri = new URI(fullRequestURI);
            String requestPath = uri.getPath();
            String query = uri.getQuery();

            Map<String, String> queryParams = parseQueryParams(query);
            Map<String, String> requestHeaders = parseRequestHeaders(bufferedReader);
            Map<String, String> headers = new LinkedHashMap<>();
            byte[] responseBodyBytes = null;
            String contentType = "text/html;charset=utf-8";
            String statusCode = "200 OK";

            if ("/".equals(requestPath)) {
                responseBodyBytes = "Hello world!".getBytes(StandardCharsets.UTF_8);
            } else if ("/login".equals(requestPath) && queryParams.containsKey("account")) {
                Optional<User> user = InMemoryUserRepository.findByAccount(queryParams.get("account"));
                if (user.isEmpty() || !user.get().checkPassword(queryParams.get("password"))) {
                    statusCode = "401 Unauthorized";
                    URL resource = getClass().getClassLoader().getResource("static/401.html");
                    if (resource != null) {
                        responseBodyBytes = Files.readAllBytes(Path.of(resource.toURI()));
                        log.atInfo().log("로그인 실패");
                    }
                } else {
                    statusCode = "302 Found";
                    headers.put("Location", "/index.html");
                    responseBodyBytes = "로그인 성공".getBytes();
                    log.atInfo().log("user: {}", user.toString());
                }
            } else {
                String resourcePath;
                if ("/login".equals(requestPath)) {
                    resourcePath = "static/login.html";
                } else {
                    resourcePath = "static" + requestPath;
                }

                URL resource = getClass().getClassLoader().getResource(resourcePath);
                if (resource != null) {
                    responseBodyBytes = Files.readAllBytes(Path.of(resource.toURI()));
                    if (resourcePath.endsWith(".css")) {
                        contentType = "text/css";
                    }
                }
            }

            if (responseBodyBytes == null) {
                statusCode = "404 Not Found";
                URL resource404 = getClass().getClassLoader().getResource("static/404.html");
                if (resource404 != null) {
                    responseBodyBytes = Files.readAllBytes(Path.of(resource404.toURI()));
                } else {
                    responseBodyBytes = "404 Not Found".getBytes(StandardCharsets.UTF_8);
                }
            }

            headers.put("Content-Type", contentType);
            headers.put("Content-Length", String.valueOf(responseBodyBytes.length));

            StringBuilder headerBuilder = new StringBuilder();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                headerBuilder.append(entry.getKey())
                        .append(": ")
                        .append(entry.getValue())
                        .append(" \r\n");
            }
            headerBuilder.append("\r\n");

            String responseHeaders = headerBuilder.toString();

            outputStream.write(("HTTP/1.1 " + statusCode + " \r\n").getBytes());
            outputStream.write(responseHeaders.getBytes(StandardCharsets.UTF_8));
            outputStream.write(responseBodyBytes);
            outputStream.flush();

        } catch (IOException | UncheckedServletException | URISyntaxException e) {
            log.atError().log(e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            log.atWarn().log(e.getMessage());
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> queryParams = new HashMap<>();
        if (query != null && !query.isEmpty()) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                    queryParams.put(key, value);
                }
            }
        }
        return queryParams;
    }

    private Map<String, String> parseRequestHeaders(BufferedReader bufferedReader) throws IOException {
        Map<String, String> requestHeaders = new HashMap<>();
        while (true) {
            String line = bufferedReader.readLine();
            if (line == null || line.isEmpty()) {
                break;
            }
            String[] headerParts = line.split(": ", 2);
            if (headerParts.length == 2) {
                requestHeaders.put(headerParts[0], headerParts[1]);
            }
        }
        return requestHeaders;
    }
}
