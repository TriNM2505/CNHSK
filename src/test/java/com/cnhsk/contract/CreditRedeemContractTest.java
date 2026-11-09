package com.cnhsk.contract;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.nio.file.*;
import java.util.*;
import com.cnhsk.auth.service.BootstrapAdminRunner;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@ActiveProfiles({"test","dev"})
@TestPropertySource(properties={
        "springdoc.api-docs.enabled=true",
        "springdoc.packages-to-scan=com.cnhsk.learning.credit",
        "spring.datasource.url=jdbc:h2:mem:credit-redeem-contract;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
class CreditRedeemContractTest {
    private static final String PATH="/api/credits/redeem";
    @MockitoBean private BootstrapAdminRunner bootstrap;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlers;

    @Test void redeemOperationAndAllResponseSchemasMatchLockedContract() throws Exception {
        JsonNode expected=source(), actual=generated();
        JsonNode operation=expected.path("paths").path(PATH).path("post");
        JsonNode runtime=actual.path("paths").path(PATH).path("post");
        assertThat(runtime.path("operationId")).isEqualTo(operation.path("operationId"));
        assertThat(fields(runtime.path("responses"))).isEqualTo(fields(operation.path("responses")));
        Set<JsonNode> security=new HashSet<>(); runtime.path("security").forEach(security::add);
        Set<JsonNode> expectedSecurity=new HashSet<>(); expected.path("security").forEach(expectedSecurity::add);
        assertThat(security).isEqualTo(expectedSecurity);
        assertThat(runtime.path("requestBody").path("required").asBoolean()).isTrue();
        schema(operation.path("requestBody").path("content").path("application/json").path("schema"),
                runtime.path("requestBody").path("content").path("application/json").path("schema"), "request");
        for(String code:fields(operation.path("responses"))) {
            JsonNode response=resolve(expected,operation.path("responses").path(code));
            schema(response.path("content").path("application/json").path("schema"),
                    runtime.path("responses").path(code).path("content").path("application/json").path("schema"),code);
        }
        JsonNode expectedParameter=resolve(expected,operation.path("parameters").get(0));
        assertThat(runtime.path("parameters")).hasSize(1);
        JsonNode parameter=runtime.path("parameters").get(0);
        assertThat(parameter.path("name")).isEqualTo(expectedParameter.path("name"));
        assertThat(parameter.path("in")).isEqualTo(expectedParameter.path("in"));
        assertThat(parameter.path("required").asBoolean()).isEqualTo(expectedParameter.path("required").asBoolean());
        schema(expectedParameter.path("schema"),parameter.path("schema"),"XSRF header");
    }

    @Test void redeemAndPlanSchemasHaveExactPropertiesRequiredTypesEnumsAndNullableExpiry() throws Exception {
        JsonNode expected=source().path("components").path("schemas");
        JsonNode actual=generated().path("components").path("schemas");
        for(String name:List.of("RedeemRequest","RedeemResult","PlanStatus","PlanCode","CardType"))
            schema(expected.path(name),actual.path(name),name);
    }

    @Test void onlyPostRedeemIsRegisteredAndWalletHistoryGetRemainUnimplemented() {
        Set<String> registered=new HashSet<>();
        handlers.getHandlerMethods().forEach((mapping,handler)-> {
            for(String path:mapping.getPatternValues()) {
                if(path.equals(PATH)) mapping.getMethodsCondition().getMethods().forEach(method->registered.add(method.name()+" "+path));
                if(List.of("/api/me/credits","/api/me/credit-transactions").contains(path))
                    assertThat(mapping.getMethodsCondition().getMethods()).doesNotContain(org.springframework.web.bind.annotation.RequestMethod.GET);
            }
        });
        assertThat(registered).containsExactly("POST "+PATH);
    }

    private JsonNode source() throws Exception {
        return new ObjectMapper(new YAMLFactory()).readTree(Files.readString(Path.of("specs/013-credit-cards/contracts/openapi-credit.yaml")));
    }
    private JsonNode generated() throws Exception {
        return mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    private static JsonNode resolve(JsonNode document,JsonNode node) {
        return node.has("$ref")&&node.path("$ref").asText().startsWith("#/")?document.at(node.path("$ref").asText().substring(1)):node;
    }
    private static void schema(JsonNode expected,JsonNode actual,String location) {
        assertThat(actual.isMissingNode()).as(location).isFalse();
        if(expected.has("$ref")) {
            String reference=expected.path("$ref").asText();
            assertThat(actual.path("$ref").asText()).as(location)
                    .isEqualTo(reference.startsWith("#/")?reference:"#/components/schemas/ErrorResponse");
            return;
        }
        assertThat(types(actual)).as("types %s",location).isEqualTo(types(expected));
        assertThat(strings(actual.path("required"))).as("required %s",location).isEqualTo(strings(expected.path("required")));
        assertThat(fields(actual.path("properties"))).as("properties %s",location).isEqualTo(fields(expected.path("properties")));
        for(String key:List.of("minimum","maximum","minLength","maxLength","pattern","format","enum")) {
            if(expected.has(key)) assertThat(actual.path(key)).as("%s %s",location,key).isEqualTo(expected.path(key));
        }
        expected.path("properties").fields().forEachRemaining(field->schema(field.getValue(),actual.path("properties").path(field.getKey()),location+"."+field.getKey()));
        if(expected.has("items")) schema(expected.path("items"),actual.path("items"),location+"[]");
    }
    private static Set<String> fields(JsonNode node) {
        Set<String> result=new HashSet<>(); node.fieldNames().forEachRemaining(result::add); return result;
    }
    private static Set<String> strings(JsonNode node) {
        Set<String> result=new HashSet<>(); node.forEach(value->result.add(value.asText())); return result;
    }
    private static Set<String> types(JsonNode node) {
        return node.path("type").isArray()?strings(node.path("type")):node.has("type")?Set.of(node.path("type").asText()):Set.of();
    }
}

