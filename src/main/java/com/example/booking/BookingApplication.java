package com.example.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// Les notifications partent hors du fil de la requête : une réservation ne
// doit pas attendre un serveur SMTP, ni échouer parce qu'il est injoignable.
@EnableAsync
@EnableScheduling
public class BookingApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookingApplication.class, args);
	}

}
