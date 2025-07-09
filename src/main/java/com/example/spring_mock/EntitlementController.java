package com.example.spring_mock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
public class EntitlementController {

    private List<Map<String, Object>> mockDefinitions;

    /**
     * This method runs after the controller is constructed.
     * It reads and parses the JSON mock file from the resources folder.
     */
    @PostConstruct
    public void init() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            InputStream inputStream = new ClassPathResource("entitlement-mocks.json").getInputStream();
            mockDefinitions = objectMapper.readValue(inputStream, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            // In a real application, you'd want more robust error handling
            e.printStackTrace();
            // Initialize with an empty list to prevent NullPointerExceptions
            mockDefinitions = List.of();
        }
    }

    @PostMapping("/ics-management/internal/entitlementStatus")
    public ResponseEntity<Object> getEntitlementStatus(@RequestHeader Map<String, String> headers, @RequestBody JsonNode requestBody) {
        // Find the first mock definition that matches the incoming request
        for (Map<String, Object> mock : mockDefinitions) {
            if (matches(mock, headers, requestBody)) {
                Map<String, Object> response = (Map<String, Object>) mock.get("response");
                int status = (int) response.get("status");
                Object responseBody = response.get("body");
                return new ResponseEntity<>(responseBody, HttpStatus.valueOf(status));
            }
        }

        // If no mock definition matches, return a default error response
        return new ResponseEntity<>("No matching mock definition found.", HttpStatus.NOT_FOUND);
    }

    /**
     * Checks if an incoming request matches a mock definition.
     * @param mock The mock definition from the JSON file.
     * @param headers The headers from the incoming request.
     * @param requestBody The body from the incoming request.
     * @return true if the request matches the mock, false otherwise.
     */
    private boolean matches(Map<String, Object> mock, Map<String, String> headers, JsonNode requestBody) {
        Map<String, Object> request = (Map<String, Object>) mock.get("request");
        Map<String, String> mockHeaders = (Map<String, String>) request.get("headers");
        Map<String, String> mockBody = (Map<String, String>) request.get("body");

        // Check headers
        for (Map.Entry<String, String> entry : mockHeaders.entrySet()) {
            String headerName = entry.getKey();
            String expectedValue = entry.getValue();
            String actualValue = headers.get(headerName.toLowerCase());

            if (actualValue == null) {
                return false;
            }

            if (!"ANY".equalsIgnoreCase(expectedValue) && !Objects.equals(expectedValue, actualValue)) {
                return false;
            }
        }

        // Check body fields
        for (Map.Entry<String, String> entry : mockBody.entrySet()) {
            String fieldName = entry.getKey();
            String expectedValue = entry.getValue();
            JsonNode actualValueNode = requestBody.get(fieldName);

            if (actualValueNode == null) {
                return false;
            }
            // For simplicity, we are checking exact string matches here.
            // You can extend this logic for more complex matching (e.g., UUID format, etc.)
            if (!Objects.equals(expectedValue, actualValueNode.asText())) {
                // For the case of an invalid API Key, the body might not matter
                if (!"INVALID_API_KEY".equals(headers.get("x-api-key"))) {
                    return false;
                }
            }
        }

        return true;
    }
}
