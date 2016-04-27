package com.bertramlabs.plugins.gstomp;

public interface StompChannelInterface {

    public void setStompClient(StompClient stompClient);

    public StompClient getStompClient();

    public Boolean connect();

    public void disconnect();

    public Boolean isConnected();

    public void sendMessage(String message);

    public void sendStompFrame(StompFrame frame);

    public void handleMessage(String message);
}