package com.example.msdepositbank.handler;

import com.example.msdepositbank.models.entities.*;
import com.example.msdepositbank.models.entities.dto.CreateDepositWithCardDTO;
import com.example.msdepositbank.services.BillService;
import com.example.msdepositbank.services.DebitService;
import com.example.msdepositbank.services.IDepositService;
import com.example.msdepositbank.services.TransactionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Component
@Slf4j(topic = "DEPOSIT_HANDLER")
public class DepositHandler {
    private final IDepositService depositService;
    private final BillService billService;
    private final TransactionService transactionService;
    private final DebitService debitService;
    @Autowired
    public DepositHandler(IDepositService depositService, BillService billService, TransactionService transactionService, DebitService debitService) {
        this.depositService = depositService;
        this.billService = billService;
        this.transactionService = transactionService;
        this.debitService = debitService;
    }

    public Mono<ServerResponse> findAll(ServerRequest request){
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
                .body(depositService.findAll(), Deposit.class);
    }

    public Mono<ServerResponse> findById(ServerRequest request){
        String id = request.pathVariable("id");
        return depositService.findById(id).flatMap(deposit -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(deposit))
                .switchIfEmpty(Mono.error(new RuntimeException("DEPOSIT DOES NOT EXIST")));
    }

    public Mono<ServerResponse> findByAccountNumber(ServerRequest request){
        String accountNumber = request.pathVariable("accountNumber");
        log.info("ACCOUNT_NUMBER_WEBCLIENT {}", accountNumber);
        return billService.findByAccountNumber(accountNumber).flatMap(p -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(p))
                .switchIfEmpty(Mono.error(new RuntimeException("THE ACCOUNT NUMBER DOES NOT EXIST")));
    }

    public Mono<ServerResponse> createDepositWithCardNumber(ServerRequest request){
        Mono<CreateDepositWithCardDTO> createDepositWithCardDTO = request.bodyToMono(CreateDepositWithCardDTO.class);
        Mono<Transaction> transactionMono = Mono.just(new Transaction());
        return Mono.zip(createDepositWithCardDTO, transactionMono)
                .zipWhen(data -> debitService.findByCardNumber(data.getT1().getCardNumber()))
                .zipWhen(result -> {
                    Transaction transaction = result.getT1().getT2();
                    transaction.setTransactionType("DEPOSIT");
                    transaction.setTransactionAmount(result.getT1().getT1().getAmount());
                    transaction.setDescription(result.getT1().getT1().getDescription());
                    List<Acquisition> acquisitions = result.getT2().getAssociations();
                    Acquisition acquisition = acquisitions.stream()
                            .filter(acq-> acq.getBill().getBalance() > result.getT1().getT1().getAmount())
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("The retire amount exceeds the available balance in yours accounts"));
                    Bill bill = acquisition.getBill();
                    bill.setBalance(bill.getBalance() + result.getT1().getT1().getAmount());
                    transaction.setBill(bill);

                    return transactionService.createTransaction(transaction);
                })
                .zipWhen(result -> {
                    List<Acquisition> acquisitions = result.getT1().getT2().getAssociations().stream()
                            .peek(rx -> {
                        if (Objects.equals(rx.getBill().getAccountNumber(), result.getT2().getBill().getAccountNumber())){
                            rx.setBill(result.getT2().getBill());
                        }
                    }).collect(Collectors.toList());
                    //validate is principal
                    Acquisition currentAcq = acquisitions.stream()
                            .filter(acquisition -> Objects.equals(acquisition.getBill().getAccountNumber(), result.getT2().getBill().getAccountNumber()))
                            .findFirst().orElseThrow(() -> new RuntimeException("The account does not exist in this card"));
                    Boolean isPrincipal = result.getT1().getT2().getPrincipal().getIban().equals(currentAcq.getIban());
                    if (Boolean.TRUE.equals(isPrincipal)){
                        result.getT1().getT2().getPrincipal().setBill(result.getT2().getBill());
                    }
                    Debit debit = new Debit();
                    debit.setAssociations(acquisitions);
                    debit.setPrincipal(result.getT1().getT2().getPrincipal());
                    debit.setCardNumber(result.getT1().getT2().getCardNumber());
                    return debitService.updateDebit(debit);
                })
                .flatMap(response -> {
                    CreateDepositWithCardDTO depositWithCardDTO = response.getT1().getT1().getT1().getT1();
                    Deposit deposit = new Deposit();
                    deposit.setAmount(depositWithCardDTO.getAmount());
                    deposit.setDescription(depositWithCardDTO.getDescription());
                    deposit.setBill(response.getT1().getT2().getBill());
                    return depositService.create(deposit);
                })
                .flatMap(depositCreate ->
                        ServerResponse.created(URI.create("/deposit/".concat(depositCreate.getId())))
                                .contentType(APPLICATION_JSON)
                                .bodyValue(depositCreate))
                .onErrorResume(e -> Mono.error(new RuntimeException(e.getMessage())));
    }

    public Mono<ServerResponse> createDeposit(ServerRequest request){
        Mono<Deposit> retire = request.bodyToMono(Deposit.class);
        return retire.flatMap(depositRequest ->  billService.findByAccountNumber(depositRequest.getBill().getAccountNumber())
                        .flatMap(billR -> {
                            billR.setBalance(billR.getBalance() + depositRequest.getAmount());
                            return billService.updateBill(billR);
                        })
                        .flatMap(bilTransaction -> {
                            Transaction transaction = new Transaction();
                            transaction.setTransactionType("DEPOSIT");
                            transaction.setTransactionAmount(depositRequest.getAmount());
                            transaction.setBill(bilTransaction);
                            transaction.setDescription(depositRequest.getDescription());
                            return transactionService.createTransaction(transaction);
                        })
                        .flatMap(currentTransaction -> {
                            depositRequest.setBill(currentTransaction.getBill());
                            return depositService.create(depositRequest);
                        })).flatMap(retireUpdate -> ServerResponse.created(URI.create("/retire/".concat(retireUpdate.getId())))
                        .contentType(APPLICATION_JSON)
                        .bodyValue(retireUpdate))
                .onErrorResume(e -> Mono.error(new RuntimeException("Error update deposit")));
    }
}
