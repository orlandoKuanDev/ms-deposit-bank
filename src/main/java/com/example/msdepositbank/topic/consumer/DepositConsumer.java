package com.example.msdepositbank.topic.consumer;

import com.example.msdepositbank.handler.DepositHandler;
import com.example.msdepositbank.models.entities.dto.CreateDepositWithCardDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

@Slf4j(topic = "DEPOSIT_CONSUMER_KAFKA")
@Component
public class DepositConsumer {
    private static final String SERVICE_CREATE_DEPOSIT_TOPIC = "service-create-deposit-topic";
    private static final String GROUP_ID = "deposit-group";
    private final DepositHandler depositHandler;
    private final ObjectMapper objectMapper;

    @Autowired
    public DepositConsumer(DepositHandler depositHandler, ObjectMapper objectMapper) {
        this.depositHandler = depositHandler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = SERVICE_CREATE_DEPOSIT_TOPIC, groupId = GROUP_ID)
    public Disposable retrieveSavedDeposit(String data) throws Exception {
        log.info("data from kafka listener (acquisition) =>"+data);
        CreateDepositWithCardDTO depositWithCardDTO= objectMapper.readValue(data, CreateDepositWithCardDTO.class );
        return Mono.just(depositWithCardDTO)
                .as(depositHandler::createDeposit)
                .log()
                .subscribe();
    }
}
