package com.cnhsk.learning.credit;

import java.nio.charset.StandardCharsets;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "cnhsk.billing")
public class BillingConfig {

    private String cardCodePepper;
    private int freeMonthlyLimit = 10;

    public String getCardCodePepper() {
        return cardCodePepper;
    }

    public void setCardCodePepper(String cardCodePepper) {
        this.cardCodePepper = cardCodePepper;
    }

    public int getFreeMonthlyLimit() {
        return freeMonthlyLimit;
    }

    public void setFreeMonthlyLimit(int freeMonthlyLimit) {
        this.freeMonthlyLimit = freeMonthlyLimit;
    }

    @AssertTrue(message = "cnhsk.billing.card-code-pepper >= 32 byte")
    public boolean isCardCodePepperValid() {
        return cardCodePepper != null
                && cardCodePepper.getBytes(StandardCharsets.UTF_8).length >= 32;
    }

    @Override
    public String toString() {
        return "BillingConfig{cardCodePepper=[REDACTED]}";
    }
}
