package com.samir.pricecomparator;

import java.io.IOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PriceComparatorApplication {

  public static void main(String[] args) throws IOException {
    SpringApplication.run(PriceComparatorApplication.class, args);
  }

}
