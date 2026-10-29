package com.cnhsk;

import static org.assertj.core.api.Assertions.assertThat;

import com.cnhsk.learning.content.importer.ImportMaintenance;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class CnhskApplicationTests {

    // Context H2 không có bảng learning.service_requests; runner khôi phục nhập dữ liệu kiểm ở integration test Testcontainers.
    @MockitoBean
    private ImportMaintenance importMaintenance;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void contextLoads() {
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        assertThat(sessionFactory.getJdbcServices().getDialect()).isInstanceOf(H2Dialect.class);
    }
}
