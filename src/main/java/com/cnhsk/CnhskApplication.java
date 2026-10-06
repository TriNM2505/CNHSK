package com.cnhsk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CNHSK - ung dung duy nhat cua he thong.
 *
 * Kien truc Modular Monolith: ba module nghiep vu auth / learning / community
 * chay chung mot tien trinh, chung mot database (ba schema).
 *
 * Module chi duoc goi nhau qua cac interface trong goi <module>.api.
 * ModuleBoundaryTest kiem tra dieu nay moi lan chay test.
 */
@SpringBootApplication
public class CnhskApplication {

    public static void main(String[] args) {
        SpringApplication.run(CnhskApplication.class, args);
    }
}
