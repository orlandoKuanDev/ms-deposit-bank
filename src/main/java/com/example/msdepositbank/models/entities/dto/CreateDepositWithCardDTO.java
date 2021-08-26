package com.example.msdepositbank.models.entities.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import reactor.core.publisher.Mono;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class CreateDepositWithCardDTO {
    private String cardNumber;
    private Double amount;
    private String description;
    private String accountNumber;
}
