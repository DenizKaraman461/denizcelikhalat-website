package com.denizcelikhalat.katalog.model;

/**
 * Checkout (ödeme/sipariş onayı) formu için basit DTO. Entity değildir, DB'ye yazılmaz.
 * customerEmail formda yok; giriş yapan kullanıcıdan alınır.
 */
public class CheckoutForm {

    private String customerName;
    private String customerPhone;
    private String deliveryAddress;
    private String customerNote;
    private boolean preInfoAccepted;
    private boolean distanceSalesAccepted;

    public CheckoutForm() {
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getCustomerNote() {
        return customerNote;
    }

    public void setCustomerNote(String customerNote) {
        this.customerNote = customerNote;
    }

    public boolean isPreInfoAccepted() {
        return preInfoAccepted;
    }

    public void setPreInfoAccepted(boolean preInfoAccepted) {
        this.preInfoAccepted = preInfoAccepted;
    }

    public boolean isDistanceSalesAccepted() {
        return distanceSalesAccepted;
    }

    public void setDistanceSalesAccepted(boolean distanceSalesAccepted) {
        this.distanceSalesAccepted = distanceSalesAccepted;
    }
}
