package com.bertramlabs.plugins.gstomp;

import com.bertramlabs.plugins.gstomp.sockjs.SockJsStompChannel;
import com.bertramlabs.plugins.gstomp.ws.WebSocketOnCloseInterceptor;
import groovy.json.JsonOutput;
import groovy.lang.Closure;

import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.*;

public class StompClient implements WebSocketOnCloseInterceptor {
    public static Integer STOMP_CONNECTION_TIMEOUT = 30000;
    private Integer senderHeartbeat = 0;
    private Integer heartbeat = 10000;
    private Boolean autoReconnect = false;
    private Integer maxRetries = 0;
    private Boolean connected;
    private Boolean disconnecting = false;
    private List<String> acceptedProtocols = new ArrayList<String>(Arrays.asList("1.0", "1.1", "1.2"));
    private String login;
    private String passcode;
    private StompChannelInterface stompChannel;

    private Map<String, String> subscriptions = new HashMap<String, String>();
    private Map<String, String> pendingReceipts = new HashMap<String, String>();
    private Integer subscriptionIdIncrementer = 0;


    public static StompClient overSockJs(URL endpointURL, String sessionId, Map headers) {
        SockJsStompChannel stompChannel = new SockJsStompChannel(endpointURL, sessionId, headers);
        return new StompClient(stompChannel);
    }

    public StompClient(StompChannelInterface stompChannel) {
        this.connected = false;
        this.stompChannel = stompChannel;
        stompChannel.setStompClient(this);
    }

    /**
     * Instantiates a connection request to the underlying Channel
     */
    public void connect() {
        if (this.connected) {
            throw new IllegalStateException("The STOMP client is already connected");
        }
        stompChannel.connect();
        Integer counter = STOMP_CONNECTION_TIMEOUT;
        while (!this.connected) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {

            }
            counter -= 250;
            if (counter <= 0) {
                stompChannel.disconnect();
                try {
                    throw new SocketTimeoutException();
                } catch (SocketTimeoutException e) {

                }
            }
        }
    }

    private void reconnect() {
        if (this.connected) {
            throw new IllegalStateException("The STOMP client is already connected");
        }
        Integer retryCounter = maxRetries;
        Boolean connectSucceeded = false;
        while (!connectSucceeded && (maxRetries == 0 || retryCounter > 0)) {
            try {
                this.connect();
                connectSucceeded = true;
            } catch (Exception e) {
                if (maxRetries > 0) {
                    retryCounter--;
                } else {
                }

                try {
                    Thread.sleep(15000);
                } catch (InterruptedException e1) {

                }
            }
        }
        if (!connectSucceeded) {
        }
    }

    /**
     * Used to disconnect the stomp client interface
     */
    public void disconnect() {
        if (!this.connected) {
            throw new IllegalStateException("The STOMP client is not yet connected");
        }
        try {
            this.disconnecting = true;
            String receiptId = String.valueOf((int) (Math.random() * 100));
            HashMap<String, String> srt = new HashMap<String, String>();
            srt.put("receipt-id", receiptId);
            StompFrame disconnectFrame = new StompFrame("DISCONNECT", srt);
            stompChannel.sendStompFrame(disconnectFrame);

            Integer counter = 5000;

            while (!Boolean.valueOf(pendingReceipts.get(receiptId))) {

                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                counter -= 250;
                if (!this.connected) {
                    return;
                }
                if (counter <= 0) {
                    break;
                }
            }
            this.connected = false;
            pendingReceipts.remove(receiptId);
            stompChannel.disconnect();
        } finally {
            this.subscriptions = new HashMap<String, String>();
            this.disconnecting = false;
        }
    }

    public Boolean isConnected() {
        return this.connected;
    }

    public void setConnected(Boolean connected) {
        this.connected = connected;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPasscode() {
        return passcode;
    }

    public void setPasscode(String passcode) {
        this.passcode = passcode;
    }

    public Integer getSenderHeartbeat() {
        return senderHeartbeat;
    }

    public void setSenderHeartbeat(Integer senderHeartbeat) {
        this.senderHeartbeat = senderHeartbeat;
    }

    public Integer getHeartbeat() {
        return heartbeat;
    }

    public void setHeartbeat(Integer heartbeat) {
        this.heartbeat = heartbeat;
    }

    public Boolean getAutoReconnect() {
        return autoReconnect;
    }

    public void setAutoReconnect(Boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    public void setMaxRetries(Integer retryCount) {
        this.maxRetries = retryCount;
    }

    public List<String> getSupportedProtocolVersions() {
        return this.acceptedProtocols;
    }

    public void sendSTOMPConnectRequest() {
        Map<String, String> connectHeaders = new HashMap<String, String>();
        List<String> supportedProtocolVersions = getSupportedProtocolVersions();

//        String k = "";
//        for (String s : supportedProtocolVersions) {
//            k += s + ",";
//        }
//

        connectHeaders['accept-version'] = getSupportedProtocolVersions().join(",");
        connectHeaders.put("heart-beat", senderHeartbeat + "," + heartbeat);

        if (login != null) {
            connectHeaders.put("login", login);
        }
        if (passcode != null) {
            connectHeaders.put("passcode", passcode);
        }

        StompFrame stompFrame = new StompFrame("CONNECT", connectHeaders, "");

        stompChannel.sendStompFrame(stompFrame);
    }

    private void sendMessage(StompFrame frame) {
        if (!connected) {
            throw new IllegalStateException("The STOMP client is not yet connected");
        }

        stompChannel.sendStompFrame(frame);
    }

    public void handleMessage(String message) {
        StompFrame frame = extractFrameFromMessage(message);

        switch (frame.command) {
            case "CONNECTED":
                this.connected = true;
                if (this.subscriptions != null) {
                    recoverSubscriptions();
                }
                break;
            case "RECEIPT":
                handleReceiptFrame(frame);
                break;
            case "ERROR":
                this.connected = false;
                this.stompChannel.disconnect();
                if (this.autoReconnect && !this.disconnecting) {
                    reconnect();
                }
                break;
            case "MESSAGE":
                handleMessageFrame(frame);
                break;
        }
    }

    private void handleReceiptFrame(StompFrame frame) {
        pendingReceipts.put(frame.headers.get("receipt-id"), "true");
    }

    private void handleMessageFrame(StompFrame frame) throws Exception {
        String subscriptionId = frame.headers.get("subscription");
        def subscription = subscriptions.get(subscriptionId);
        if (subscription.destination == frame.headers['destination']) {
            try {
                def result = subscription.callback.call(frame)
                if (frame.headers['ack']) {
                    if (result == false) {
                        this.nack(frame.headers.get("ack"));
                    } else {
                        this.ack(frame.headers.get("ack"));

                    }
                }
            } catch (Exception ex) {
                this.nack(frame.headers.get("ack"));
                throw ex;
            }
        }
    }

    public void ack(String ackId) {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("id", ackId);
        stompChannel.sendStompFrame(new StompFrame("ACK", headers));
    }

    public void nack(String ackId) {
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("id", ackId);
        stompChannel.sendStompFrame(new StompFrame("NACK", headers));
    }

    public void send(String destination, Map<String, String> headers, String message) {
        if (!connected) {
            throw new IllegalStateException("The STOMP client is not yet connected");
        }
        Map<String, String> frameHeaders = new HashMap<String, String>();
        frameHeaders.put("destination", destination);
        System.out.println(frameHeaders);
        System.out.println("************************");
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                frameHeaders.put(entry.getKey(), entry.getValue());
            }
        }
        if (message != null) {
            if (frameHeaders.get("content-type") == null) {
                frameHeaders.put("content-type", "text/plain");
            }
            frameHeaders.put("content-length", String.valueOf(message.length()));

        }

        StompFrame frame = new StompFrame("SEND", frameHeaders, message);
        stompChannel.sendStompFrame(frame);
    }

    /**
     * STOMP Send Command to send a Json Encoded Object to the Server
     *
     * @param destination The destination queue/topic/exchange we are sending to
     * @param headers Any custom headers
     * @param message The Message Object we are encoding as JSON
     */
    public void send(String destination, Map<String, String> headers = null, Object message) {
        if (!connected) {
            throw new IllegalStateException("The STOMP client is not yet connected");
        }
        if (message != null) {
            Map<String, String> frameHeaders = new HashMap<String, String>();
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    frameHeaders.put(entry.getKey(), entry.getValue());
                }
            }
            frameHeaders.put("content-type", "application/json");
            String jsonMessage = JsonOutput.toJson(message);
            send(destination, frameHeaders, jsonMessage);
        } else {
            send(destination, headers);
        }
    }

    /**
     * Subscribes a Closure to handle responses from a STOMP Queue
     *
     * @param destination
     * @param callback
     * @param headers
     * @return subscriptionId
     */
    public Integer subscribe(String destination, Closure callback, Map headers = null) {
        if (!connected) {
            throw new IllegalStateException("The STOMP client is not yet connected");
        }
        Map<String, String> frameHeaders = new HashMap<String, String>();
        frameHeaders.put("destination", destination);
        frameHeaders.put("id", String.valueOf(subscriptionIdIncrementer + 1))
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                frameHeaders.put(entry.getKey(), entry.getValue());
            }

        }
        //***********************************************************
        subscriptions[frameHeaders.get("id")] =
                [callback                     :
                         callback, destination: destination, headers: headers]

        //***********************************
        StompFrame frame = new StompFrame("SUBSCRIBE", frameHeaders);
        stompChannel.sendStompFrame(frame);
        return frameHeaders.get("id");
    }

    /**
     * Internally handles recovery of subscriptions in the event a connection is lost and re-instantiated
     */
    private void recoverSubscriptions() {
        this.subscriptions?.each {
            subscriptionEntry ->

                Map frameHeaders = [destination                                    :
                                            subscriptionEntry.value.destination, id: subscriptionEntry.key]
                if (subscriptionEntry.value.headers) {
                    frameHeaders += subscriptionEntry.value.headers
                }
                StompFrame frame = new StompFrame('SUBSCRIBE', frameHeaders)
                stompChannel.sendStompFrame(frame);
        }
    }

    /**
     * Unsubscribe all events tied to a specific destination or callback Closure
     *
     * @param destination
     * @param callback (optional) pass null if you want to unsubscribe from the destination entirely
     */
    public void unsubscribe(String destination, Closure callback) {
        if (!connected) {
            throw new IllegalStateException("The STOMP client is not yet connected");
        }
        Map matchedSubscriptions = frameHeaders.findAll {
            entry ->
                if (callback) {
                    return entry.value.destination == destination && entry.value.callback == callback
                } else {
                    return entry.value.destination == destination
                }
        }

        matchedSubscriptions?.each {
            entry ->
                ubsubscribe(entry.key.toInteger())
        }
    }

    public void ubsubscribe(Integer id) {
        if (!connected) {
            throw new IllegalStateException("The STOMP client is not yet connected");
        }

        Map<String, String> map = new HashMap<String, String>();
        map.put("id", String.valueOf(id));
        stompChannel.sendStompFrame(new StompFrame("UNSUBSCRIBE", map));
        subscriptions.remove(id.toString());
    }

    /**
     * Extracts a STOMP formatted String body into a StompFrame object
     *
     * @param message The input String to be parsed
     * @return StompFrame
     */
    private StompFrame extractFrameFromMessage(String message) {
        String[] lines = message.split("\n");
//        def bodyArgs;
        List<String> bodyArgs;
        String command = "";
        if (lines.length > 0) command = lines[0];


        String body;
        Map<String, String> headers = new HashMap<String, String>();
        Boolean blankLineFound = false;

        if (lines.length > 1) {

            for (int i = 1; i < lines.length; i++) {
                if (blankLineFound) {
                    if (bodyArgs == null) {
                        bodyArgs = new ArrayList<String>();
                        bodyArgs.add(lines[i]);
                    } else bodyArgs.add(lines[i]);
                } else if (!blankLineFound && lines[i] == "") {
                    blankLineFound = true;
                } else {
                    String[] headerArgs = lines[i].split(":");
                    if (headerArgs.length == 2) {
                        headers.put(headerArgs[0], headerArgs[1]);

                    }

                }
            }
        }

        if (bodyArgs != null) {
            body = bodyArgs.join("\n").replaceAll("\u0000", "");
        }

        return new StompFrame(command, headers, body);
    }

    @Override
    public void onClose() {
        if (this.connected && !this.disconnecting) {
            this.connected = false;
            if (this.autoReconnect) {
                this.reconnect();
            }
        }
    }
}