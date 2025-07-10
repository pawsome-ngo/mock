package com.example.spring_mock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Data;

@Controller
public class JsonMockController {

    // Injects the path from application.properties
    @Value("${mock.files.path}")
    private String mockFilesPath;

    private final Map<String, JSONArray> endpointMocks = new ConcurrentHashMap<>();

    /**
     * This method now reads from the external filesystem path.
     */
    @PostConstruct
    public void init() {
        try {
            File folder = new File(mockFilesPath);
            // Create the directory if it doesn't exist
            if (!folder.exists()) {
                folder.mkdirs();
            }

            File[] listOfFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));

            if (listOfFiles == null) return;

            for (File file : listOfFiles) {
                if (file.isFile()) {
                    try (FileReader reader = new FileReader(file)) {
                        JSONTokener tokener = new JSONTokener(reader);
                        JSONObject root = new JSONObject(tokener);
                        String endpoint = root.getString("endpoint");
                        JSONArray mocks = root.getJSONArray("mocks");
                        endpointMocks.put(endpoint, mocks);
                        System.out.println("Loaded mock for endpoint: " + endpoint + " from " + file.getName());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @GetMapping("/json-mocks")
    public String listJsonMocks(Model model) {
        model.addAttribute("endpoints", endpointMocks.keySet());
        return "json-mocks";
    }

    @GetMapping("/json-mocks/details")
    public String showMockDetails(@RequestParam String endpoint, Model model) {
        JSONArray mocks = endpointMocks.get(endpoint);
        if (mocks == null) {
            return "redirect:/json-mocks";
        }

        List<Map<String, String>> mockDetails = new ArrayList<>();
        for (int i = 0; i < mocks.length(); i++) {
            JSONObject mock = mocks.getJSONObject(i);
            String name = mock.optString("name", "Unnamed Mock Case #" + (i + 1));
            String content = mock.toString(4);

            Map<String, String> detailMap = new HashMap<>();
            detailMap.put("name", name);
            detailMap.put("content", content);
            mockDetails.add(detailMap);
        }

        model.addAttribute("endpoint", endpoint);
        model.addAttribute("mockDetails", mockDetails);
        return "json-mock-details";
    }

    @Data
    private static class AddMockPayload {
        private String endpoint;
        private String newMockJson;
    }

    /**
     * This method now writes directly to the external file system.
     */
    @PostMapping("/json-mocks/add")
    @ResponseBody
    public ResponseEntity<String> addMock(@RequestBody AddMockPayload payload) {
        try {
            String fileName = findFileNameForEndpoint(payload.getEndpoint());
            if (fileName == null) {
                return new ResponseEntity<>("{\"error\": \"Could not find file for endpoint.\"}", HttpStatus.NOT_FOUND);
            }

            Path filePath = Paths.get(mockFilesPath, fileName);
            File file = filePath.toFile();

            String content = new String(Files.readAllBytes(filePath));
            JSONObject root = new JSONObject(content);
            JSONArray mocks = root.getJSONArray("mocks");

            JSONObject newMock = new JSONObject(payload.getNewMockJson());
            mocks.put(newMock);

            try (FileWriter fileWriter = new FileWriter(file)) {
                fileWriter.write(root.toString(4)); // Pretty print with 4 spaces
            }

            endpointMocks.put(payload.getEndpoint(), mocks); // Refresh in-memory cache

            return new ResponseEntity<>("{\"message\": \"Mock added successfully and saved permanently.\"}", HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("{\"error\": \"Failed to add mock: " + e.getMessage() + "\"}", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String findFileNameForEndpoint(String targetEndpoint) throws IOException {
        File folder = new File(mockFilesPath);
        File[] listOfFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (listOfFiles == null) return null;

        for (File file : listOfFiles) {
            try (FileReader reader = new FileReader(file)) {
                JSONTokener tokener = new JSONTokener(reader);
                JSONObject root = new JSONObject(tokener);
                if (targetEndpoint.equals(root.optString("endpoint"))) {
                    return file.getName();
                }
            }
        }
        return null;
    }

    @RequestMapping("/mock/**")
    @ResponseBody
    public ResponseEntity<String> handleDynamicMock(
            HttpServletRequest request,
            @RequestHeader Map<String, String> headers,
            @RequestBody(required = false) String requestBodyString) {

        String requestURI = request.getRequestURI().replace("/mock", "");
        JSONArray mocks = endpointMocks.get(requestURI);

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
            return Objects.equals(body.get(matchKey).toString(), mockBody.get(matchKey).toString());
        } else {
            return body != null && mockBody.similar(body);
        }
    }
}
