package webserver;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import db.DataBase;
import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HttpRequestUtils;
import util.IOUtils;

public class RequestHandler extends Thread {
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        LOGGER.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            // TODO 사용자 요청에 대한 처리는 이 곳에 구현하면 된다.
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line = br.readLine();

            if (line == null) {
                return;
            }

            String url = HttpRequestUtils.getUrl(line);
            Map<String, String> headers = new HashMap<>();

            if (url.equals("/")) {
                url = "/index.html";
            }

            if (url.startsWith("/user/create")) {
                while (!(line = br.readLine()).equals("")) {
                    LOGGER.debug("line: {}", line);
                    String[] tokens = line.split(": ");
                    if (tokens.length == 2) {
                        headers.put(tokens[0], tokens[1]);
                    }
                }
                String requestBody = IOUtils.readData(br, Integer.parseInt(headers.get("Content-Length")));
                LOGGER.debug("body : {}", requestBody);

                Map<String, String> params = HttpRequestUtils.parseQueryString(requestBody);
                User user = new User(params.get("userId"), params.get("password"), params.get("name"), params.get("email"));
                LOGGER.debug("User : {}", user);

                DataBase.addUser(user);

                DataOutputStream dos = new DataOutputStream(out);
                response302Header(dos);
                return;
            }

            if (url.equals("/user/login")) {
                while (!(line = br.readLine()).equals("")) {
                    HttpRequestUtils.Pair pair = HttpRequestUtils.parseHeader(line);
                    headers.put(pair.getKey(), pair.getValue());
                }
                String requestBody = IOUtils.readData(br, Integer.parseInt(headers.get("Content-Length")));
                LOGGER.debug("body : {}", requestBody);

                Map<String, String> params = HttpRequestUtils.parseQueryString(requestBody);
                User inputUser = new User(params.get("userId"), params.get("password"));
                User userInDB = Optional.ofNullable(DataBase.findUserById(params.get("userId")))
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

                LOGGER.debug("User : {}", inputUser);

                DataOutputStream dos = new DataOutputStream(out);

                if (userInDB.matchUser(inputUser)) {
                    LOGGER.debug("login success!!");
                    responseHeaderWithCookie(dos, "loggedIn= true");
                } else {
                    LOGGER.debug("login fail!!");
                    response302Header(dos);
                }
                return;
            }

            if(url.endsWith(".css")){
                DataOutputStream dos = new DataOutputStream(out);
                byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
                response200HeaderWithCss(dos, body.length);
                responseBody(dos, body);
            }

            DataOutputStream dos = new DataOutputStream(out);
            byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
            response200Header(dos, body.length);
            responseBody(dos, body);
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }
    }

    private void response302Header(DataOutputStream dos) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: /\r\n");
            dos.writeBytes("\r\n");
            LOGGER.debug("302 Found");
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }
    }

    private void responseHeaderWithCookie(DataOutputStream dos, String cookieStatus) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: /\r\n");
            dos.writeBytes("setCookie:" + cookieStatus + "/\r\n");
            dos.writeBytes("\r\n");
            LOGGER.debug("302 Found");
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }
    }

    private void response200HeaderWithCss(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/css;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }
    }
}
