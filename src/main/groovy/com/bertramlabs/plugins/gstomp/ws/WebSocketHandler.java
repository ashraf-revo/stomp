package com.bertramlabs.plugins.gstomp.ws;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.SslContextConfigurator;
import org.glassfish.tyrus.client.SslEngineConfigurator;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.websocket.*;
import javax.websocket.MessageHandler.Whole;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

public class WebSocketHandler extends Endpoint implements Whole<String> {
    public Session userSession = null;
    private boolean connected = false;
    private MessageHandler messageHandler;
    private WebSocketOnCloseInterceptor closeInterceptor;

    public WebSocketHandler(URI endpointURI, ClientEndpointConfig clientEndpointConfig) {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();

            container.connectToServer(this, clientEndpointConfig, endpointURI);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public WebSocketHandler(URI endpointURI, Map headers) {
        try {
            WebSocketConfigurator configurator = new WebSocketConfigurator(headers);
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(
                                java.security.cert.X509Certificate[] certs, String authType) {
                        }
                        public void checkServerTrusted(
                                java.security.cert.X509Certificate[] certs, String authType) {
                        }
                    }

            };
            SslEngineConfigurator sslEngineConfigurator = new SslEngineConfigurator(new SslContextConfigurator());
            sslEngineConfigurator.setHostVerificationEnabled(false);
            sslEngineConfigurator.getSslContext().init(null, trustAllCerts, new java.security.SecureRandom());
            ClientEndpointConfig clientEndpointConfig = ClientEndpointConfig.Builder.create().configurator(configurator).build();
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            ClientManager client = ClientManager.createClient(container);
            if (endpointURI.getScheme().equals("wss")) {
                client.getProperties().put(ClientProperties.SSL_ENGINE_CONFIGURATOR, sslEngineConfigurator);
            }
            client.connectToServer(this, clientEndpointConfig, endpointURI);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void disconnect() {
        if (!this.connected) return;

        if (this.userSession != null)
            try {
                this.userSession.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "User Disconnect Requested"));
            } catch (IOException ignored) {
            }
        this.connected = false;
    }

    public Boolean isConnected() {
        return this.connected;
    }

    public void onOpen(Session userSession, EndpointConfig config) {
        this.userSession = userSession;
        this.connected = true;
        this.userSession.addMessageHandler(this);
    }

    public void onClose(Session userSession, CloseReason reason) {
        this.userSession = null;
        if (this.closeInterceptor != null) this.closeInterceptor.onClose();

        this.connected = false;
    }

    public void onError(Session session, Throwable thr) {
    }

    public void onMessage(String text) {
        if (this.messageHandler != null) this.messageHandler.handleMessage(text);
    }

    public void addMessageHandler(MessageHandler msgHandler) {
        this.messageHandler = msgHandler;
    }

    public void addCloseInterceptor(WebSocketOnCloseInterceptor closeInterceptor) {
        this.closeInterceptor = closeInterceptor;
    }

    public void sendMessage(String message) {
        this.userSession.getAsyncRemote().sendText(message);
    }
}