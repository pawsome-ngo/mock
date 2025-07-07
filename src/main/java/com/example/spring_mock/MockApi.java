package com.example.spring_mock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class MockApi {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String endpoint;

    @Column(length = 2048)
    private String requestBody;

    private int responseCode;
    private String requestType;

    private boolean hasResponseBody;

    @Column(length = 2048)
    private String responseBody;
}