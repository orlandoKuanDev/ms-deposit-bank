package com.example.msdepositbank.models.entities.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class CreateDepositWithAccountDTO {
    private Double amount;
    private String description;
    private String accountNumber;
}
