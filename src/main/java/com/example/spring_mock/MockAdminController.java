package com.example.spring_mock;

import lombok.Data;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class MockAdminController {

    @Autowired
    private MockFileService mockFileService;

    @GetMapping("/")
    public String listJsonMocks(Model model) {
        model.addAttribute("endpoints", mockFileService.getEndpoints());
        return "json-mocks";
    }

    @GetMapping("/details")
    public String showMockDetails(@RequestParam String endpoint, Model model) {
        JSONArray mocks = mockFileService.getMocksForEndpoint(endpoint);
        if (mocks == null) {
            return "redirect:/";
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
    private static class CreateEndpointPayload {
        private String endpoint;
    }

    @Data
    private static class AddMockPayload {
        private String endpoint;
        private String newMockJson;
    }

    @Data
    private static class DeleteMockPayload {
        private String endpoint;
        private int index;
    }

    @Data
    private static class UpdateMockPayload {
        private String endpoint;
        private int index;
        private String updatedMockJson;
    }

    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<String> createEndpoint(@RequestBody CreateEndpointPayload payload) {
        try {
            mockFileService.createEndpointFile(payload.getEndpoint());
            return new ResponseEntity<>("{\"message\": \"Endpoint created successfully.\"}", HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>("{\"error\": \"" + e.getMessage() + "\"}", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/add")
    @ResponseBody
    public ResponseEntity<String> addMock(@RequestBody AddMockPayload payload) {
        try {
            mockFileService.addMockToFile(payload.getEndpoint(), payload.getNewMockJson());
            return new ResponseEntity<>("{\"message\": \"Mock added successfully.\"}", HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("{\"error\": \"" + e.getMessage() + "\"}", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * NEW METHOD: Handles updating an existing mock case.
     */
    @PostMapping("/update-mock")
    @ResponseBody
    public ResponseEntity<String> updateMock(@RequestBody UpdateMockPayload payload) {
        try {
            mockFileService.updateMockInFile(payload.getEndpoint(), payload.getIndex(), payload.getUpdatedMockJson());
            return new ResponseEntity<>("{\"message\": \"Mock updated successfully.\"}", HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("{\"error\": \"" + e.getMessage() + "\"}", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/delete-endpoint")
    @ResponseBody
    public ResponseEntity<String> deleteEndpoint(@RequestBody CreateEndpointPayload payload) {
        try {
            mockFileService.deleteEndpointFile(payload.getEndpoint());
            return new ResponseEntity<>("{\"message\": \"Endpoint deleted successfully.\"}", HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("{\"error\": \"" + e.getMessage() + "\"}", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/delete-mock")
    @ResponseBody
    public ResponseEntity<String> deleteMockCase(@RequestBody DeleteMockPayload payload) {
        try {
            mockFileService.deleteMockFromFile(payload.getEndpoint(), payload.getIndex());
            return new ResponseEntity<>("{\"message\": \"Mock case deleted successfully.\"}", HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("{\"error\": \"" + e.getMessage() + "\"}", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
