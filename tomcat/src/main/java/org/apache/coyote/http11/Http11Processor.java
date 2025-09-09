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
            String[] requestLineParts = requestLine.split(" ");
            String requestHttpMethod = requestLineParts[0];
            String fullRequestURI = requestLineParts[1];

            Map<String, String> requestHeaders = parseRequestHeaders(bufferedReader);

            URI uri = new URI(fullRequestURI);
            String requestPath = uri.getPath();
            Map<String, String> requestParams = new HashMap<>();

            if (uri.getQuery() != null) {
                requestParams.putAll(parseUrlEncoded(uri.getQuery()));
            }

            if ("POST".equals(requestHttpMethod)) {
                int contentLength = Integer.parseInt(requestHeaders.getOrDefault("Content-Length", "0"));
                if (contentLength > 0) {
                    String requestBody = parseRequestBody(contentLength, bufferedReader);
                    requestParams.putAll(parseUrlEncoded(requestBody));
                }
            }

            Map<String, String> headers = new LinkedHashMap<>();
            byte[] responseBodyBytes = null;
            String contentType = "text/html;charset=utf-8";
            String statusCode = "200 OK";

            if ("/".equals(requestPath) && "GET".equals(requestHttpMethod)) {
                responseBodyBytes = "Hello world!".getBytes(StandardCharsets.UTF_8);

            } else if ("/login".equals(requestPath) && "POST".equals(requestHttpMethod)) {
                String account = requestParams.get("account");
                String password = requestParams.get("password");

                Optional<User> user = InMemoryUserRepository.findByAccount(account);

                if (user.isPresent() && user.get().checkPassword(password)) {
                    statusCode = "302 Found";
                    headers.put("Location", "/index.html");
                    responseBodyBytes = "로그인 성공".getBytes(StandardCharsets.UTF_8);
                    log.info("로그인 성공. user: {}", user.get());
                } else {
                    statusCode = "401 Unauthorized";
                    URL resource = getClass().getClassLoader().getResource("static/401.html");
                    if (resource != null) {
                        responseBodyBytes = Files.readAllBytes(Path.of(resource.toURI()));
                    }
                    log.info("로그인 실패");
                }
            } else {
                String resourcePath;
                if ("/login".equals(requestPath) && "GET".equals(requestHttpMethod)) {
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
                        .append("\r\n");
            }

            outputStream.write(("HTTP/1.1 " + statusCode + "\r\n").getBytes(StandardCharsets.UTF_8));
            outputStream.write(headerBuilder.toString().getBytes(StandardCharsets.UTF_8));
            outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
            outputStream.write(responseBodyBytes);
            outputStream.flush();

        } catch (IOException | URISyntaxException | UncheckedServletException e) {
            log.atError().log(e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            log.atWarn().log(e.getMessage());
        }
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

    private Map<String, String> parseRequestHeaders(BufferedReader bufferedReader) throws IOException {
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
