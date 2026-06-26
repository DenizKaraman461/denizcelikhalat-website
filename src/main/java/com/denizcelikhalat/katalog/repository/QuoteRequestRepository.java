package com.denizcelikhalat.katalog.repository;

import com.denizcelikhalat.katalog.model.QuoteRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuoteRequestRepository extends JpaRepository<QuoteRequest, Long> {

    // Admin paneli: en yeni talep en üstte.
    List<QuoteRequest> findAllByOrderByCreatedAtDesc();
}
