package org.opencron.server.support;

import org.opencron.server.domain.Terminal;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.opencron.common.util.CommonUtils.notEmpty;

public class TerminalSession implements Serializable {

    //key--->WebSocketSession value--->TerminalClient
    public static Map<WebSocketSession, TerminalClient> terminalSession = new ConcurrentHashMap<WebSocketSession, TerminalClient>(0);

    public static TerminalClient get(WebSocketSession key) {
        return terminalSession.get(key);
    }

    public static TerminalClient get(String key) {
        for (Map.Entry<WebSocketSession, TerminalClient> entry : terminalSession.entrySet()) {
            TerminalClient client = entry.getValue();
            if (client.getClientId().equals(key)) {
                return client;
            }
        }
        return null;
    }

    public static void put(WebSocketSession key, TerminalClient terminalClient) {
        terminalSession.put(key, terminalClient);
    }

    public static TerminalClient remove(WebSocketSession key) {
        return terminalSession.remove(key);
    }

    public static boolean isOpened(Terminal terminal) {
        for (Map.Entry<WebSocketSession, TerminalClient> entry : terminalSession.entrySet()) {
            if (entry.getValue().getTerminal().equals(terminal)) {
                return true;
            }
        }
        return false;
    }

    public static List<TerminalClient> findClient(Serializable sessionId) throws IOException {
        List<TerminalClient> terminalClients = new ArrayList<TerminalClient>(0);
        if (notEmpty(terminalSession)) {
            for (Map.Entry<WebSocketSession, TerminalClient> entry : terminalSession.entrySet()) {
                TerminalClient terminalClient = entry.getValue();
                if (terminalClient != null && terminalClient.getTerminal() != null) {
                    if (sessionId.equals(terminalClient.getHttpSessionId())) {
                        terminalClients.add(terminalClient);
                    }
                }
            }
        }
        return terminalClients;
    }

    public static WebSocketSession findSession(Terminal terminal) {
        for (Map.Entry<WebSocketSession, TerminalClient> entry : terminalSession.entrySet()) {
            TerminalClient client = entry.getValue();
            if (client.getTerminal().equals(terminal)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static void exit(String httpSessionId) throws IOException {
        if (notEmpty(terminalSession)) {
            for (Map.Entry<WebSocketSession, TerminalClient> entry : terminalSession.entrySet()) {
                TerminalClient terminalClient = entry.getValue();
                if (terminalClient.getHttpSessionId().equals(httpSessionId)) {
                    terminalClient.disconnect();
                    terminalClient.getWebSocketSession().sendMessage(new TextMessage("Sorry! Session was invalidated, so opencron Terminal changed to closed. "));
                    terminalClient.getWebSocketSession().close();
                }
            }
        }
    }

}