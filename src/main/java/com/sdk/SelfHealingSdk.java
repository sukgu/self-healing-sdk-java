package com.sdk;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SelfHealingSdk {
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public SelfHealingSdk(String baseUrl) {
        this.baseUrl = baseUrl;
        this.objectMapper = new ObjectMapper();
    }

    // Java representation of ElementFingerprint
    public static class ElementFingerprint {
        public UUID id;
        public Map<String, String> attributes;
        public List<String> selectors;
    }

    public static class HealRequest {
        public String failed_selector;
        public Map<String, String> context;
    }

    public static class HealResponse {
        public String healed_selector;
        public float confidence;
        public String details;
    }

    public static class RegisterRequest {
        public ElementFingerprint fingerprint;
    }

    // Helper method to POST JSON and get response as string
    private String postJson(String endpoint, Object obj) throws IOException {
        URI uri = URI.create(baseUrl + endpoint);
        URL url = uri.toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");

        String jsonRequest = objectMapper.writeValueAsString(obj);
        try(OutputStream os = conn.getOutputStream()) {
            os.write(jsonRequest.getBytes());
            os.flush();
        }

        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed : HTTP error code : " + conn.getResponseCode());
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())))) {
            StringBuilder sb = new StringBuilder();
            String output;
            while ((output = br.readLine()) != null) {
                sb.append(output);
            }
            return sb.toString();
        }
    }

    // Heal selector method
    public HealResponse healSelector(HealRequest request) throws IOException {
        String responseJson = postJson("/heal-selector", request);
        return objectMapper.readValue(responseJson, HealResponse.class);
    }

    // Register fingerprint method
    public void registerFingerprint(RegisterRequest request) throws IOException {
        postJson("/register-fingerprint", request);
    }

    // Fetch all fingerprints (GET request)
    public List<ElementFingerprint> getAllFingerprints() throws IOException {
        URI uri = URI.create(baseUrl + "/all-fingerprints");
        URL url = uri.toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed : HTTP error code : " + conn.getResponseCode());
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())))) {
            StringBuilder sb = new StringBuilder();
            String output;
            while ((output = br.readLine()) != null) {
                sb.append(output);
            }
            return objectMapper.readValue(sb.toString(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ElementFingerprint.class));
        }
    }
}
