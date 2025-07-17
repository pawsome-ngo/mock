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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MockFileService {

    @Value("${mock.files.path}")
    private String mockFilesPath;

    private final Map<String, JSONArray> endpointMocks = new ConcurrentHashMap<>();
    private final Map<String, String> endpointToFileMap = new ConcurrentHashMap<>();
    private final Map<String, String> endpointToNameMap = new ConcurrentHashMap<>();

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
        endpointToFileMap.clear();
        endpointToNameMap.clear();
        File baseDir = new File(mockFilesPath);
        scanDirectory(baseDir, "");
    }

    private void scanDirectory(File dir, String currentPath) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, currentPath + file.getName() + "/");
            } else if (file.getName().toLowerCase().endsWith(".json")) {
                try (FileReader reader = new FileReader(file)) {
                    JSONTokener tokener = new JSONTokener(reader);
                    JSONObject root = new JSONObject(tokener);
                    String name = root.getString("name");
                    String endpoint = root.getString("endpoint");
                    JSONArray mocks = root.getJSONArray("mocks");

                    endpointMocks.put(endpoint, mocks);
                    endpointToFileMap.put(endpoint, currentPath + file.getName());
                    endpointToNameMap.put(endpoint, name);
                    System.out.println("Loaded mock: '" + name + "' for endpoint: " + endpoint);
                }
            }
        }
    }

    public Map<String, List<Map<String, String>>> getCategorizedEndpoints() {
        Map<String, List<Map<String, String>>> categorized = new HashMap<>();
        endpointToFileMap.forEach((endpoint, filePath) -> {
            String category = new File(filePath).getParent();
            if (category == null) {
                category = "Uncategorized";
            }
            String name = endpointToNameMap.get(endpoint);

            Map<String, String> endpointInfo = new HashMap<>();
            endpointInfo.put("name", name);
            endpointInfo.put("endpoint", endpoint);

            categorized.computeIfAbsent(category, k -> new ArrayList<>()).add(endpointInfo);
        });
        return categorized;
    }

    public JSONArray getMocksForEndpoint(String endpoint) {
        return endpointMocks.get(endpoint);
    }

    public void createEndpointFile(String category, String name, String endpoint) throws IOException {
        Path categoryPath = Paths.get(mockFilesPath, category);
        if (!Files.exists(categoryPath)) {
            Files.createDirectories(categoryPath);
        }

        // Generate filename from the descriptive name
        String fileName = name.trim().toLowerCase().replaceAll("\\s+", "-") + ".json";
        Path filePath = categoryPath.resolve(fileName);
        File file = filePath.toFile();

        if (file.exists()) {
            throw new IOException("A mock file with this name already exists in the category.");
        }

        JSONObject root = new JSONObject();
        root.put("name", name);
        root.put("endpoint", endpoint);
        root.put("mocks", new JSONArray());

        try (FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.write(root.toString(4));
        }

        endpointMocks.put(endpoint, new JSONArray());
        endpointToFileMap.put(endpoint, category + "/" + fileName);
        endpointToNameMap.put(endpoint, name);
    }

    public void addMockToFile(String endpoint, String newMockJson) throws IOException {
        String filePathStr = findFileNameForEndpoint(endpoint);
        if (filePathStr == null) {
            throw new IOException("Could not find file for endpoint.");
        }
        Path filePath = Paths.get(mockFilesPath, filePathStr);
        String content = new String(Files.readAllBytes(filePath));
        JSONObject root = new JSONObject(content);
        JSONArray mocks = root.getJSONArray("mocks");
        mocks.put(new JSONObject(newMockJson));

        try (FileWriter fileWriter = new FileWriter(filePath.toFile())) {
            fileWriter.write(root.toString(4));
        }
        endpointMocks.put(endpoint, mocks);
    }

    public void updateMockInFile(String endpoint, int index, String updatedMockJson) throws IOException {
        String filePathStr = findFileNameForEndpoint(endpoint);
        if (filePathStr == null) {
            throw new IOException("Could not find file for endpoint.");
        }
        Path filePath = Paths.get(mockFilesPath, filePathStr);
        String content = new String(Files.readAllBytes(filePath));
        JSONObject root = new JSONObject(content);
        JSONArray mocks = root.getJSONArray("mocks");

        if (index >= 0 && index < mocks.length()) {
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
        String filePathStr = findFileNameForEndpoint(endpoint);
        if (filePathStr == null) {
            throw new IOException("Could not find file for endpoint.");
        }
        Path filePath = Paths.get(mockFilesPath, filePathStr);
        Files.delete(filePath);
        endpointMocks.remove(endpoint);
        endpointToFileMap.remove(endpoint);
        endpointToNameMap.remove(endpoint);
    }

    public void deleteMockFromFile(String endpoint, int index) throws IOException {
        String filePathStr = findFileNameForEndpoint(endpoint);
        if (filePathStr == null) {
            throw new IOException("Could not find file for endpoint.");
        }
        Path filePath = Paths.get(mockFilesPath, filePathStr);
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

    private String findFileNameForEndpoint(String targetEndpoint) {
        return endpointToFileMap.get(targetEndpoint);
    }
}
