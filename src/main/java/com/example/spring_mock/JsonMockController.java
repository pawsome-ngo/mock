package com.example.spring_mock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Objects;

/**
 * This controller is responsible for handling incoming API requests and serving mock responses
 * based on the definitions found in the JSON files. It acts as the core engine for the mock server.
 */
@RestController
public class JsonMockController {

    /**
     * Injects the service that manages all file system operations for the mock JSON files.
     */
    @Autowired
    private MockFileService mockFileService;

    /**
     * A "catch-all" endpoint that intercepts all incoming requests that do not match a more specific
     * controller (like the UI endpoints in MockControllerUI). It acts as the engine for the mock server.
     * It dynamically finds the appropriate mock response by matching the request's URI, method, headers, and body.
     *
     * @param request The incoming HttpServletRequest, used to get the URI and method.
     * @param headers A map of all headers from the incoming request.
     * @param requestBodyString The raw request body as a string. It is optional.
     * @return A ResponseEntity containing the mock response body and status code, or a 404 error if no match is found.
     */
    @RequestMapping("/**")
    public ResponseEntity<String> handleDynamicMock(
            HttpServletRequest request,
            @RequestHeader Map<String, String> headers,
            @RequestBody(required = false) String requestBodyString) {

        String requestURI = request.getRequestURI();
        JSONArray mocks = mockFileService.getMocksForEndpoint(requestURI);

        // If no mock file is configured for the requested endpoint, return 404.
        if (mocks == null) {
            return new ResponseEntity<>("{\"error\": \"No mock definition found for endpoint: " + requestURI + "\"}", HttpStatus.NOT_FOUND);
        }

        String requestMethod = request.getMethod();
        JSONObject requestBody = null;
        try {
            // Safely parse the request body string into a JSONObject.
            if (requestBodyString != null && !requestBodyString.isEmpty()) {
                requestBody = new JSONObject(requestBodyString);
            }
        } catch (JSONException e) {
            // If the body is not valid JSON, it won't match any mock that expects a JSON body.
            requestBody = null;
        }

        // Loop through each mock case defined for the endpoint to find a match.
        for (int i = 0; i < mocks.length(); i++) {
            JSONObject mock = mocks.getJSONObject(i);
            if (matches(mock, requestMethod, headers, requestBody)) {
                // If a match is found, construct and return the defined response.
                JSONObject response = mock.getJSONObject("response");
                int status = response.getInt("status");
                String responseBody = response.get("body").toString();
                return new ResponseEntity<>(responseBody, HttpStatus.valueOf(status));
            }
        }

        // If the loop completes without finding a match, return a 404 error.
        return new ResponseEntity<>("{\"error\": \"No matching mock pattern found for this request.\"}", HttpStatus.NOT_FOUND);
    }

    /**
     * Checks if an incoming request's properties match a given mock definition.
     *
     * @param mock The JSONObject representing a single mock case from the JSON file.
     * @param method The HTTP method of the incoming request (e.g., "GET", "POST").
     * @param headers The headers of the incoming request.
     * @param body The parsed JSONObject of the incoming request body.
     * @return true if all conditions of the mock are met, false otherwise.
     */
    private boolean matches(JSONObject mock, String method, Map<String, String> headers, JSONObject body) {
        JSONObject mockRequest = mock.getJSONObject("request");
        String mockMethod = mockRequest.getString("method");

        // 1. Check if the HTTP method matches.
        if (!Objects.equals(mockMethod, method)) {
            return false;
        }

        // 2. Check if all headers defined in the mock are present and have matching values.
        JSONObject mockHeaders = mockRequest.getJSONObject("headers");
        for (String key : mockHeaders.keySet()) {
            // Headers are case-insensitive, so we check against the lowercase key.
            if (!Objects.equals(mockHeaders.getString(key), headers.get(key.toLowerCase()))) {
                return false;
            }
        }

        // 3. Check if the request body matches based on the defined strategy.
        JSONObject mockBody = mockRequest.optJSONObject("body");
        String matchKey = mockRequest.optString("matchKey", null);

        // If the mock expects no body, the request must also have no body.
        if (mockBody == null || mockBody.isEmpty()) {
            return body == null || body.isEmpty();
        }

        // If a 'matchKey' is specified, perform a partial match on that key only.
        if (matchKey != null && !matchKey.equals("null") && !matchKey.isEmpty()) {
            // The key must exist in both the mock and the incoming request body.
            if (body == null || !body.has(matchKey) || !mockBody.has(matchKey)) {
                return false;
            }
            // Compare the values of the specified key.
            Object actualValue = body.get(matchKey);
            Object expectedValue = mockBody.get(matchKey);
            return Objects.equals(actualValue.toString(), expectedValue.toString());
        } else {
            // If no 'matchKey' is specified, perform a deep, order-independent comparison of the entire JSON body.
            return body != null && mockBody.similar(body);
        }
    }
}
