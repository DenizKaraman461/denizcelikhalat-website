package com.denizcelikhalat.katalog.model;

public enum OrderStatus {
    PENDING,     // Sipariş alındı, ödeme/onay bekliyor
    PREPARING,   // Hazırlanıyor
    SHIPPED,     // Kargoya verildi
    DELIVERED,   // Teslim edildi
    CANCELLED    // İptal edildi
}
