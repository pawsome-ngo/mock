package com.example.spring_mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
public class UserProfileMockController {

    // The list now holds strongly-typed MockDefinition objects
    private List<MockDefinition> mockDefinitions;

    // --- POJO classes to represent the JSON structure ---

    @Data
    private static class MockConfiguration {
        private String endpoint;
        private List<MockDefinition> mocks;
    }

    @Data
    private static class MockDefinition {
        private String name;
        private MockRequest request;
        private MockResponse response;
    }

    @Data
    private static class MockRequest {
        private String method;
        private Map<String, String> headers;
        private JsonNode body;
    }

    @Data
    private static class MockResponse {
        private int status;
        private JsonNode body;
    }

    /**
     * This method runs once after the controller is created.
     * It reads and parses the user-profile-mocks.json file into type-safe objects.
     */
    @PostConstruct
    public void init() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            InputStream inputStream = new ClassPathResource("user-profile-mocks.json").getInputStream();

            // Directly deserialize into the top-level configuration object
            MockConfiguration fullConfig = objectMapper.readValue(inputStream, MockConfiguration.class);

            // Get the list of mock definitions
            this.mockDefinitions = fullConfig.getMocks();

        } catch (Exception e) {
            e.printStackTrace();
            // If the file can't be loaded, initialize with an empty list to avoid errors
            this.mockDefinitions = List.of();
        }
    }

    /**
     * This single endpoint handles all methods (GET, PUT, DELETE) for the user profile URL.
     * It finds a matching mock from the JSON file and returns the defined response.
     */
    @RequestMapping("/api/v1/user/profile")
    public ResponseEntity<Object> handleUserProfileMocks(
            HttpServletRequest request,
            @RequestHeader Map<String, String> headers,
            @RequestBody(required = false) JsonNode requestBody) {

        String requestMethod = request.getMethod();

        // Iterate through all mock definitions to find a match
        for (MockDefinition mock : mockDefinitions) {
            if (matches(mock, requestMethod, headers, requestBody)) {
                // No casting needed here, using getter methods
                MockResponse response = mock.getResponse();
                return new ResponseEntity<>(response.getBody(), HttpStatus.valueOf(response.getStatus()));
            }
        }

        // If no definition matches the request, return a 404 Not Found error
        return new ResponseEntity<>("No matching mock definition found for this request.", HttpStatus.NOT_FOUND);
    }

    /**
     * Helper method to check if an incoming request matches a specific mock definition.
     */
    private boolean matches(MockDefinition mock, String method, Map<String, String> headers, JsonNode body) {
        // No casting needed here, using getter methods
        MockRequest mockRequest = mock.getRequest();

        // 1. Check if the HTTP method matches
        if (!Objects.equals(mockRequest.getMethod(), method)) {
            return false;
        }

        // 2. Check if all required headers match
        Map<String, String> mockHeaders = mockRequest.getHeaders();
        for (Map.Entry<String, String> entry : mockHeaders.entrySet()) {
            String headerName = entry.getKey().toLowerCase();
            String expectedValue = entry.getValue();
            String actualValue = headers.get(headerName);

            if (!Objects.equals(expectedValue, actualValue)) {
                return false;
            }
        }

        // 3. Check if the request body matches
        JsonNode mockBodyNode = mockRequest.getBody();

        // If mock body is null or empty, it matches requests with no body
        if (mockBodyNode == null || mockBodyNode.isNull() || mockBodyNode.isEmpty()) {
            return body == null || body.isNull() || body.isEmpty();
        }

        // If mock body is defined, it must be equal to the request body
        return Objects.equals(mockBodyNode, body);
    }
}
