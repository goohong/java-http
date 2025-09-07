package org.apache.coyote.http11;

import com.techcourse.exception.UncheckedServletException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
             final var outputStream = connection.getOutputStream()) {

            byte[] responseBodyBytes = "Hello world!".getBytes();

            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String requestLine = bufferedReader.readLine();
            String requestURI = requestLine.split(" ")[1];

            if (!"/".equals(requestURI)) {
                Path path = Path.of(ClassLoader.getSystemResource("static" + requestURI).toURI());
                responseBodyBytes = Files.readAllBytes(path);
            }

            var responseHeaders = String.join("\r\n",
                    "HTTP/1.1 200 OK ",
                    "Content-Type: " + "text/html;charset=utf-8 ",
                    "Content-Length: " + responseBodyBytes.length + " ",
                    "\r\n");

            outputStream.write(responseHeaders.getBytes());
            outputStream.write(responseBodyBytes);
            outputStream.flush();
        } catch (IOException | UncheckedServletException | URISyntaxException e) {
            log.error(e.getMessage(), e);
        }
    }
}
