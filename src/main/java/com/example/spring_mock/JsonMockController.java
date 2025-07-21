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

@RestController
public class JsonMockController {

    @Autowired
    private MockFileService mockFileService;

    @RequestMapping("/**")
    public ResponseEntity<String> handleDynamicMock(
            HttpServletRequest request,
            @RequestHeader Map<String, String> headers,
            @RequestBody(required = false) String requestBodyString) {

        String contextPath = request.getContextPath();
        String requestURI = request.getRequestURI().substring(contextPath.length());

        // UPDATED: Explicitly ignore admin and UI-related paths.
        if (requestURI.equals("/") || requestURI.startsWith("/details") || requestURI.startsWith("/admin/")) {
            return new ResponseEntity<>("{\"error\": \"This path is reserved for the admin UI and is not a mock endpoint.\"}", HttpStatus.NOT_FOUND);
        }

        JSONArray mocks = mockFileService.getMocksForEndpoint(requestURI);

        if (mocks == null) {
            return new ResponseEntity<>("{\"error\": \"No mock definition found for endpoint: " + requestURI + "\"}", HttpStatus.NOT_FOUND);
        }

        String requestMethod = request.getMethod();
        JSONObject requestBody = null;
        try {
            if (requestBodyString != null && !requestBodyString.isEmpty()) {
                requestBody = new JSONObject(requestBodyString);
            }
        } catch (JSONException e) {
            requestBody = null;
        }

        for (int i = 0; i < mocks.length(); i++) {
            JSONObject mock = mocks.getJSONObject(i);
            if (matches(mock, requestMethod, headers, requestBody)) {
                JSONObject response = mock.getJSONObject("response");
                int status = response.getInt("status");
                String responseBody = response.get("body").toString();
                return new ResponseEntity<>(responseBody, HttpStatus.valueOf(status));
            }
        }

        return new ResponseEntity<>("{\"error\": \"No matching mock pattern found for this request.\"}", HttpStatus.NOT_FOUND);
    }

    private boolean matches(JSONObject mock, String method, Map<String, String> headers, JSONObject body) {
        JSONObject mockRequest = mock.getJSONObject("request");
        String mockMethod = mockRequest.getString("method");

        if (!Objects.equals(mockMethod, method)) {
            return false;
        }

        JSONObject mockHeaders = mockRequest.getJSONObject("headers");
        for (String key : mockHeaders.keySet()) {
            if (!Objects.equals(mockHeaders.getString(key), headers.get(key.toLowerCase()))) {
                return false;
            }
        }

        JSONObject mockBody = mockRequest.optJSONObject("body");
        String matchKey = mockRequest.optString("matchKey", null);

        if (mockBody == null || mockBody.isEmpty()) {
            return body == null || body.isEmpty();
        }

        if (matchKey != null && !matchKey.equals("null") && !matchKey.isEmpty()) {
            if (body == null || !body.has(matchKey) || !mockBody.has(matchKey)) {
                return false;
            }
            Object actualValue = body.get(matchKey);
            Object expectedValue = mockBody.get(matchKey);
            return Objects.equals(actualValue.toString(), expectedValue.toString());
        } else {
            return body != null && mockBody.similar(body);
        }
    }
}
