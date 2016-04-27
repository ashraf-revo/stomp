package com.bertramlabs.plugins.gstomp.sockjs;

import com.bertramlabs.plugins.gstomp.StompClient;
import com.bertramlabs.plugins.gstomp.ws.MessageHandler;

public class StompMessageHandler implements MessageHandler {

    private SockJsStompChannel sockHandler;
    private StompClient stompClient;

    public StompMessageHandler(SockJsStompChannel sockHandler) {
        this.sockHandler = sockHandler;
        this.stompClient = sockHandler.stompClient;
    }

    public void handleMessage(String message) {
        if (message.equals("o")) {
            stompClient.sendSTOMPConnectRequest();
            return;
        }
        if (message.startsWith("a[")) {
            String extractedMessage = message.substring(3, message.length() - 3);
            sockHandler.handleMessage(extractedMessage);
        }
    }

}
