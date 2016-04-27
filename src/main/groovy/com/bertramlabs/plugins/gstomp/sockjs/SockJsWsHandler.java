package com.bertramlabs.plugins.gstomp.sockjs;

import com.bertramlabs.plugins.gstomp.ws.WebSocketHandler;

import java.net.URI;
import java.util.Map;

public class SockJsWsHandler extends WebSocketHandler {
    public SockJsWsHandler(URI endpointURI, Map headers) {
        super(endpointURI, headers);
    }
}