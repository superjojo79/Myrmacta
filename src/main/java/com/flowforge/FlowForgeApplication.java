package com.flowforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FlowForgeApplication {

    public static void main(String[] args) {
        // Earliest possible point to flip this JDK flag — must run before the first
        // HttpURLConnection is ever created in this JVM (see WebhookRestAdapter, D-44).
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
        SpringApplication.run(FlowForgeApplication.class, args);
    }
}
