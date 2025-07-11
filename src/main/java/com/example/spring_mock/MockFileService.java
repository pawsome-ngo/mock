package com.example.spring_mock;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MockFileService {

    @Value("${mock.files.path}")
    private String mockFilesPath;

    private final Map<String, JSONArray> endpointMocks = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            File folder = new File(mockFilesPath);
            if (!folder.exists()) {
                folder.mkdirs();
            }
            loadMocks();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadMocks() throws IOException {
        endpointMocks.clear();
        File folder = new File(mockFilesPath);
        File[] listOfFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (listOfFiles == null) return;

        for (File file : listOfFiles) {
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

    public Set<String> getEndpoints() {
        return endpointMocks.keySet();
    }

    public JSONArray getMocksForEndpoint(String endpoint) {
        return endpointMocks.get(endpoint);
    }

    public void createEndpointFile(String endpoint) throws IOException {
        String fileName = endpoint.trim().replaceAll("^/|/$", "").replaceAll("/", "-") + ".json";
        Path filePath = Paths.get(mockFilesPath, fileName);
        File file = filePath.toFile();

        if (file.exists()) {
            throw new IOException("A mock file for this endpoint already exists.");
        }

        JSONObject root = new JSONObject();
        root.put("endpoint", endpoint);
        root.put("mocks", new JSONArray());

        try (FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.write(root.toString(4));
        }

        endpointMocks.put(endpoint, new JSONArray());
    }

    public void addMockToFile(String endpoint, String newMockJson) throws IOException {
        String fileName = findFileNameForEndpoint(endpoint);
        if (fileName == null) {
            throw new IOException("Could not find file for endpoint.");
        }

        Path filePath = Paths.get(mockFilesPath, fileName);
        String content = new String(Files.readAllBytes(filePath));
        JSONObject root = new JSONObject(content);
        JSONArray mocks = root.getJSONArray("mocks");
        mocks.put(new JSONObject(newMockJson));

        try (FileWriter fileWriter = new FileWriter(filePath.toFile())) {
            fileWriter.write(root.toString(4));
        }

        endpointMocks.put(endpoint, mocks);
    }

    /**
     * NEW METHOD: Updates a specific mock case in a file.
     * @param endpoint The endpoint URL to identify the file.
     * @param index The index of the mock case to update in the array.
     * @param updatedMockJson The new JSON content for the mock case.
     * @throws IOException If there's an error reading or writing the file.
     */
    public void updateMockInFile(String endpoint, int index, String updatedMockJson) throws IOException {
        String fileName = findFileNameForEndpoint(endpoint);
        if (fileName == null) {
            throw new IOException("Could not find file for endpoint.");
        }
        Path filePath = Paths.get(mockFilesPath, fileName);
        String content = new String(Files.readAllBytes(filePath));
        JSONObject root = new JSONObject(content);
        JSONArray mocks = root.getJSONArray("mocks");

        if (index >= 0 && index < mocks.length()) {
            // Replace the object at the specified index with the new one.
            mocks.put(index, new JSONObject(updatedMockJson));
        } else {
            throw new IndexOutOfBoundsException("Invalid mock index for update.");
        }

        try (FileWriter fileWriter = new FileWriter(filePath.toFile())) {
            fileWriter.write(root.toString(4));
        }
        endpointMocks.put(endpoint, mocks);
    }


    public void deleteEndpointFile(String endpoint) throws IOException {
        String fileName = findFileNameForEndpoint(endpoint);
        if (fileName == null) {
            throw new IOException("Could not find file for endpoint.");
        }
        Path filePath = Paths.get(mockFilesPath, fileName);
        Files.delete(filePath);
        endpointMocks.remove(endpoint);
    }

    public void deleteMockFromFile(String endpoint, int index) throws IOException {
        String fileName = findFileNameForEndpoint(endpoint);
        if (fileName == null) {
            throw new IOException("Could not find file for endpoint.");
        }
        Path filePath = Paths.get(mockFilesPath, fileName);
        String content = new String(Files.readAllBytes(filePath));
        JSONObject root = new JSONObject(content);
        JSONArray mocks = root.getJSONArray("mocks");

        if (index >= 0 && index < mocks.length()) {
            mocks.remove(index);
        } else {
            throw new IndexOutOfBoundsException("Invalid mock index.");
        }

        try (FileWriter fileWriter = new FileWriter(filePath.toFile())) {
            fileWriter.write(root.toString(4));
        }
        endpointMocks.put(endpoint, mocks);
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
}
