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
import java.util.Map;
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

            byte[] responseBodyBytes = null;
            String contentType = "text/html;charset=utf-8";
            String statusCode = "200 OK";

            if ("/".equals(requestPath)) {
                responseBodyBytes = "Hello world!".getBytes(StandardCharsets.UTF_8);
            } else if ("/login".equals(requestPath) && queryParams.containsKey("account")) {
                User user = InMemoryUserRepository.findByAccount(queryParams.get("account"))
                        .orElseThrow(() -> new IllegalArgumentException("입력된 값과 일치하는 user가 존재하지 않습니다."));
                if (!user.checkPassword(queryParams.get("password"))) {
                    throw new IllegalArgumentException("입력된 password가 등록된 값과 일치하지 않습니다.");
                }

                log.atInfo().log("user: {}", user.toString());
            }

            String resourcePath;
            if ("/login".equals(requestPath)) {
                resourcePath = "static/login.html";
            } else {
                resourcePath = "static" + requestPath;
            }

            URL resource = ClassLoader.getSystemResource(resourcePath);
            if (resource != null) {
                responseBodyBytes = Files.readAllBytes(Path.of(resource.toURI()));
                if (resourcePath.endsWith(".css")) {
                    contentType = "text/css";
                }
            }

            if (responseBodyBytes == null) {
                statusCode = "404 Not Found";
                URL resource404 = ClassLoader.getSystemResource("static/404.html");
                if (resource404 != null) {
                    responseBodyBytes = Files.readAllBytes(Path.of(resource404.toURI()));
                } else {
                    responseBodyBytes = "404 Not Found".getBytes(StandardCharsets.UTF_8);
                }
            }

            String responseHeaders = String.join(" \r\n",
                    "HTTP/1.1 " + statusCode,
                    "Content-Type: " + contentType,
                    "Content-Length: " + responseBodyBytes.length,
                    "\r\n");

            outputStream.write(responseHeaders.getBytes(StandardCharsets.UTF_8));
            outputStream.write(responseBodyBytes);
            outputStream.flush();

        } catch (IOException | UncheckedServletException | URISyntaxException e) {
            log.error(e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            log.warn(e.getMessage());
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
