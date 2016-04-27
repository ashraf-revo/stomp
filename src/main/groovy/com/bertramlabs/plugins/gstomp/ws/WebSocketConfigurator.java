package com.bertramlabs.plugins.gstomp.ws;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.HandshakeResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by davydotcom on 8/13/15.
 */
public class WebSocketConfigurator extends ClientEndpointConfig.Configurator {
    static volatile boolean called = false;
    private Map<String, String> customHeaders = null;

    public WebSocketConfigurator(Map headers) {
        this.customHeaders = headers;
    }

    WebSocketConfigurator() {

    }

    @Override
    public void beforeRequest(Map<String, List<String>> headers) {
        called = true;
        if (customHeaders != null) {

            for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                headers.put(entry.getKey(), Arrays.asList(entry.getValue()));

            }
        }

        headers.put("Origin", Arrays.asList("http://localhost:8080"));
    }

    @Override
    public void afterResponse(HandshakeResponse handshakeResponse) {
        final Map<String, List<String>> headers = handshakeResponse.getHeaders();
        // println "received Header ${headers.get("origin")}"
        // assertEquals("*", headers.get("origin").get(0));
    }
}
