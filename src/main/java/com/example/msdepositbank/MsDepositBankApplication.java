package com.example.msdepositbank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import reactor.core.publisher.Hooks;

@SpringBootApplication
@EnableEurekaClient
public class MsDepositBankApplication {

	public static void main(String[] args) {
		Hooks.onOperatorDebug();
		SpringApplication.run(MsDepositBankApplication.class, args);
	}

}
