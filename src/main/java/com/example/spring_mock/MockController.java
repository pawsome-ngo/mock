package com.example.spring_mock;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@Controller
public class MockController {

    @Autowired
    private MockApiRepository mockApiRepository;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("mockApi", new MockApi());
        return "index";
    }

    @GetMapping("/list")
    public String listApis(Model model, @RequestParam(required = false) String endpoint) {
        List<MockApi> mockApis;
        if (endpoint != null && !endpoint.isEmpty()) {
            mockApis = mockApiRepository.findByEndpointContaining(endpoint);
        } else {
            mockApis = mockApiRepository.findAll();
        }
        model.addAttribute("mockApis", mockApis);
        model.addAttribute("endpointFilter", endpoint); // To repopulate the filter field
        return "list";
    }

    @PostMapping("/mock")
    public String createMockApi(MockApi mockApi) {
        if (mockApi.getRequestBody() != null && !mockApi.getRequestBody().isEmpty()) {
            JSONObject jsonObject = new JSONObject(mockApi.getRequestBody());
            mockApi.setRequestBody(jsonObject.toString());
        }
        mockApiRepository.save(mockApi);
        return "redirect:/list";
    }

    // New method to handle deleting an API
    @GetMapping("/delete/{id}")
    public String deleteApi(@PathVariable Long id) {
        mockApiRepository.deleteById(id);
        return "redirect:/list";
    }

    @RequestMapping("/**")
    public ResponseEntity<String> mock(HttpServletRequest request, @RequestBody(required = false) String body) {
        String endpoint = request.getRequestURI();
        // Exclude the delete endpoint from mock handling
        if (endpoint.startsWith("/delete/")) {
            return ResponseEntity.status(404).body("Not a mock endpoint.");
        }
        String requestType = request.getMethod();

        if (body != null && !body.isEmpty()) {
            JSONObject jsonObject = new JSONObject(body);
            body = jsonObject.toString();
        }

        MockApi mockApi = mockApiRepository.findByEndpointAndRequestBodyAndRequestType(endpoint, body, requestType);

        if (mockApi != null) {
            if (mockApi.isHasResponseBody()) {
                return ResponseEntity.status(mockApi.getResponseCode()).body(mockApi.getResponseBody());
            } else {
                return ResponseEntity.status(mockApi.getResponseCode()).build();
            }
        } else {
            return ResponseEntity.status(404).body("No mock found for " + endpoint);
        }
    }
}