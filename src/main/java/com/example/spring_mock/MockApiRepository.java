package com.example.spring_mock;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MockApiRepository extends JpaRepository<MockApi, Long> {

    MockApi findByEndpointAndRequestBodyAndRequestType(String endpoint, String requestBody, String requestType);

    // New method to find APIs by endpoint
    List<MockApi> findByEndpointContaining(String endpoint);
}