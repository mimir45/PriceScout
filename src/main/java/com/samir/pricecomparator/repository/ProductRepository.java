package com.samir.pricecomparator.repository;

import com.samir.pricecomparator.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByBrandIgnoreCase(String brand);

    List<Product> findByBrandIsNull();

    List<Product> findByModelIsNull();
}
