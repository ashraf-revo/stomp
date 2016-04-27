import com.bertramlabs.plugins.gstomp.StompClient;
import groovy.lang.Closure;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by revo on 4/26/16.
 */
public class Main {
    private static final String url = "http://localhost:8080/hello/";

    public static void main(String[] args) throws MalformedURLException, InterruptedException {
        Map<String, String> map = new HashMap<String, String>();
        map.put("X-API-KEY", "1234567");
        StompClient stompClient = StompClient.overSockJs(new URL(url), null, map);
        stompClient.setAutoReconnect(true);
        stompClient.connect();


//***********************************************************************************
        stompClient.subscribe("/user/topic/greetings", {
            println(it)
        });
//***********************************************************************************
        stompClient.send("/app/hello", new Message("dddddddddddd"));
        Thread.sleep(200000);
    }
}
