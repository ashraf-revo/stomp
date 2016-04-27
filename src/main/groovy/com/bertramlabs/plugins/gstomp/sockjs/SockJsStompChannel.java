package com.bertramlabs.plugins.gstomp.sockjs;

import com.bertramlabs.plugins.gstomp.StompChannelInterface;
import com.bertramlabs.plugins.gstomp.StompClient;
import com.bertramlabs.plugins.gstomp.StompFrame;
import groovy.json.StringEscapeUtils;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.UUID;

public class SockJsStompChannel implements StompChannelInterface {
    public URL endpointURL;
    public Map sockJsInfo;
    public String sessionId;
    public Integer serverId;
    public SockJsWsHandler websocketHandler;
    public StompClient stompClient;
    public Map<String, String> requestHeaders = null;
    Boolean connected = false;

    public SockJsStompChannel(URL endpointURL, String sessionId, Map headers) {
        this.endpointURL = endpointURL;
        if (sessionId != null) {
            this.sessionId = sessionId;
        } else {
            this.sessionId = UUID.randomUUID().toString();
        }
        this.requestHeaders = headers;
        this.serverId = (int) (Math.random() * 999);

    }


    public Boolean connect() {
        URL infoUrl = null;
        try {
            infoUrl = new URL(endpointURL, "info");
        } catch (MalformedURLException e) {

        }

        try {

//            String infoText = infoUrl.text
//            this.sockJsInfo = new JsonSlurper().parseText(infoText) as Map;
//
//            if (sockJsInfo.websocket) {
            return connectWs();
//            } else {
//                throw new ProtocolException("The STOMP Sockjs Interface currently only supports the websocket interface")
//            }
        } catch (Exception e) {

            return false;
        }
    }

    private Boolean connectWs() {
        URI webSocketURI = getWebsocketURI();
        this.websocketHandler = new SockJsWsHandler(webSocketURI, requestHeaders);
        websocketHandler.addMessageHandler(new StompMessageHandler(this));
        websocketHandler.addCloseInterceptor(stompClient);
        this.connected = true;
        this.sendMessage("[\"\"]");
//        this.stompClient.sendSTOMPConnectRequest();
        return true;
    }

    public Boolean isConnected() {
        return this.connected;
    }

    public void disconnect() {
        if (!this.connected) {
            return;
        }
        this.websocketHandler.disconnect();
        this.stompClient.setConnected(false);
    }


    public StompClient getStompClient() {
        return this.stompClient;
    }

    public void setStompClient(StompClient stompClient) {
        this.stompClient = stompClient;
    }

    public void sendMessage(String message) {
        this.websocketHandler.sendMessage(message);
    }

    public void sendStompFrame(StompFrame stompFrame) {
        String escapedFrame = StringEscapeUtils.escapeJava(stompFrame.toString());
        sendMessage("[\"" + escapedFrame + "\"]");
    }

    public void handleMessage(String message) {
        stompClient.handleMessage(StringEscapeUtils.unescapeJava(message).replaceAll("\u0000", ""));

    }


    protected URI getWebsocketURI() {
        String wsProtocol = "ws";
        if (endpointURL.getProtocol().toLowerCase().equals("https")) {
            wsProtocol = "wss";
        }

        String wsStringURI = wsProtocol + "://" + endpointURL.getHost();
        if (endpointURL.getPort() != -1) {
            wsStringURI += ":" + endpointURL.getPort();
        }

        wsStringURI += endpointURL.getPath() + serverId + "/" + sessionId + "/websocket";
        try {
            return new URI(wsStringURI);
        } catch (URISyntaxException e) {
            return null;
        }
    }
}