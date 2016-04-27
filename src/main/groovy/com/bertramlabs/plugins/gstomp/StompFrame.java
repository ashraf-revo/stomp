package com.bertramlabs.plugins.gstomp;

import groovy.json.JsonSlurper;

import java.util.Map;
import java.util.Set;

/**
 * Representation of a STOMP Frame
 *
 * @author David Estes
 */
public class StompFrame {
    public String command;
    public Map<String, String> headers;
    public String body;

    public StompFrame(String command, Map<String, String> headers) {
        this.command = command;
        this.headers = headers;
    }

    public StompFrame(String command, Map<String, String> headers, String body) {
        this.command = command;
        this.headers = headers;
        this.body = body;
    }


    public Object getJsonBody() {
        if (this.body != null) {
            try {
                return new JsonSlurper().parseText(this.body);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    public String toString() {
        String message = command + "\n";
        if (headers != null) {

            for (Map.Entry<String, String> entry : headers.entrySet()) {
                message += entry.getKey() + ":" + entry.getValue() + "\n";
            }
//            println "**************************************************"
        }
        message += "\n";
        if (body != null) {
            message += body;
        }
        message += "\u0000";
        return message;
    }
}
