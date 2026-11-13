package com.cnhsk.learning.reference.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cnhsk.shared.security.JwtService;
import com.cnhsk.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles({"test", "dev"})
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.packages-to-scan=com.cnhsk.learning.reference.web"})
@Transactional
class AdminReferenceControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ROOT="/api/admin/references";
    private static final String PASSWORD_HASH=new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwt;
    private long editor;
    private long item;

    @BeforeEach
    void setup() {
        editor=jdbc.queryForObject("INSERT INTO auth.users(email,password_hash,display_name) VALUES (?,?,?) RETURNING id",
                Long.class,UUID.randomUUID()+"@example.test",PASSWORD_HASH,"Tu soan");
        item=seed("DRAFT");
    }

    @Test
    void FR003_AT15_allSevenOperationsRejectGuestAndEveryNonEditorIncludingPureAdmin() throws Exception {
        List<String> operations=List.of("GET ","POST ","GET /"+item,"PUT /"+item,
                "POST /"+item+"/publish","POST /"+item+"/hide","POST /"+item+"/unhide");
        for(String operation:operations) {
            String[] parts=operation.split(" ",2);
            mvc.perform(request(HttpMethod.valueOf(parts[0]),ROOT+parts[1]).contentType(MediaType.APPLICATION_JSON)
                    .content(input("BOOK").toString())).andExpect(status().isUnauthorized());
            for(String role:List.of("LEARNER","ADMIN","TEACHER","MODERATOR","ACCOUNTANT")) {
                mvc.perform(request(HttpMethod.valueOf(parts[0]),ROOT+parts[1]).header("Authorization","Bearer "+token(role))
                        .contentType(MediaType.APPLICATION_JSON).content(input("BOOK").toString()))
                        .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("FORBIDDEN"));
            }
        }
        assertThat(audits()).isZero();
    }

    @Test
    void FR003_cookieWriteRequiresCsrfButDoesNotRequireVerifiedEmail() throws Exception {
        String access=token("CONTENT_EDITOR");
        mvc.perform(post(ROOT).cookie(new Cookie("access_token",access)).contentType(MediaType.APPLICATION_JSON)
                        .content(input("BOOK").toString()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error_code").value("CSRF_TOKEN_INVALID"));
        mvc.perform(post(ROOT).cookie(new Cookie("access_token",access),new Cookie("XSRF-TOKEN","reference-admin-test"))
                        .header("X-XSRF-TOKEN","reference-admin-test").contentType(MediaType.APPLICATION_JSON)
                        .content(input("BOOK").toString())).andExpect(status().isCreated());
    }

    @Test
    void FR012_createAndPutIgnoreServerFieldsAndPutReplacesOptionalData() throws Exception {
        ObjectNode body=input("BOOK");
        body.put("id",Long.MAX_VALUE).put("createdBy",Long.MAX_VALUE).put("status","PUBLISHED")
                .put("publishedAt","2000-01-01T00:00:00Z").put("updatedAt","2000-01-01T00:00:00Z")
                .put("sortOrder",9).put("isAffiliate",true).put("description","Old").putArray("tags").add("old");
        JsonNode created=ok(post(ROOT).content(body.toString()),201);
        long id=created.path("id").asLong();
        assertThat(id).isNotEqualTo(Long.MAX_VALUE);
        assertThat(created.path("status").asText()).isEqualTo("DRAFT");
        assertThat(created.path("publishedAt").isNull()).isTrue();
        assertThat(created.has("createdBy")).isFalse();
        JsonNode published=ok(post(ROOT+"/"+id+"/publish"),200);
        ObjectNode replacement=input("PODCAST").put("status","HIDDEN").put("createdBy",Long.MAX_VALUE);
        JsonNode updated=ok(put(ROOT+"/"+id).content(replacement.toString()),200);
        assertThat(updated.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(updated.path("publishedAt")).isEqualTo(published.path("publishedAt"));
        assertThat(updated.path("tags")).isEmpty();
        assertThat(updated.path("sortOrder").asInt()).isZero();
        assertThat(updated.path("description").isNull()).isTrue();
        assertThat(updated.has("author")).isFalse();
        assertThat(updated.has("isAffiliate")).isFalse();
        assertThat(jdbc.queryForObject("SELECT created_by FROM learning.reference_items WHERE id=?",Long.class,id)).isEqualTo(editor);
        assertThat(ok(get(ROOT+"/"+id),200)).isEqualTo(updated);
        assertThat(ok(get(ROOT).param("kind","PODCAST"),200).path("total").asLong()).isEqualTo(1);
    }

    @Test
    void FR013_AT10_publishAndPublishedEditRejectMissingAuthorWithoutChangingTheRow() throws Exception {
        jdbc.update("UPDATE learning.reference_items SET author=null WHERE id=?",item);
        fail(post(ROOT+"/"+item+"/publish"),"PUBLISH_INVALID_RESOURCE","author");
        assertThat(jdbc.queryForObject("SELECT status FROM learning.reference_items WHERE id=?",String.class,item)).isEqualTo("DRAFT");
        jdbc.update("UPDATE learning.reference_items SET status='PUBLISHED',author='Author',published_at=? WHERE id=?",
                Timestamp.from(Instant.parse("2026-11-13T00:00:00Z")),item);
        var body=input("BOOK");
        body.remove("author");
        body.put("title","Not saved");
        fail(put(ROOT+"/"+item).content(body.toString()),"PUBLISH_INVALID_RESOURCE","author");
        assertThat(jdbc.queryForMap("SELECT title,author,status FROM learning.reference_items WHERE id=?",item))
                .containsEntry("title","Demo").containsEntry("author","Author").containsEntry("status","PUBLISHED");
        assertThat(audits()).isZero();
    }

    @Test
    void FR014_AT11_hideUnhideAndRepublishControlPublicVisibilityWithoutResettingFirstPublishTime() throws Exception {
        JsonNode published=ok(post(ROOT+"/"+item+"/publish"),200);
        ok(post(ROOT+"/"+item+"/hide"),200);
        mvc.perform(get("/api/public/references/"+item)).andExpect(status().isNotFound());
        JsonNode draft=ok(post(ROOT+"/"+item+"/unhide"),200);
        assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
        assertThat(ok(post(ROOT+"/"+item+"/unhide"),200)).isEqualTo(draft);
        mvc.perform(get("/api/public/references/"+item)).andExpect(status().isNotFound());
        JsonNode again=ok(post(ROOT+"/"+item+"/publish"),200);
        assertThat(again.path("publishedAt")).isEqualTo(published.path("publishedAt"));
        mvc.perform(get("/api/public/references/"+item)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource({"YOUTUBE_CHANNEL,url,http://youtube.com,NON_HTTPS_LINK",
            "YOUTUBE_CHANNEL,url,javascript:alert(1),NON_HTTPS_LINK",
            "YOUTUBE_CHANNEL,url,https://evil.com/youtube.com,DOMAIN_NOT_ALLOWED",
            "YOUTUBE_CHANNEL,url,https://youtube.com@evil.com/x,MALFORMED_URL",
            "BOOK,thumbnailUrl,https://youtube.com/x,DOMAIN_NOT_ALLOWED",
            "BOOK,thumbnailUrl,http://i.ytimg.com/x,NON_HTTPS_LINK",
            "BOOK,thumbnailUrl,https:///x,MALFORMED_URL"})
    void FR015_AT05_AT08_urlPolicyErrorsKeepTheirFieldAndDoNotLeakInput(String kind,String field,String value,String code)
            throws Exception {
        ObjectNode body=input(kind).put(field,value);
        fail(post(ROOT).content(body.toString()),code,field);
        assertThat(audits()).isZero();
    }

    @Test
    void FR015_AT08_allowedYoutubeSubdomainAndNonBookNullFalseFieldsAreAccepted() throws Exception {
        ObjectNode body=input("YOUTUBE_CHANNEL").put("url","https://m.youtube.com/@demo").putNull("author")
                .putNull("publisher").putNull("publishedYear").put("isAffiliate",false);
        assertThat(ok(post(ROOT).content(body.toString()),201).path("kind").asText()).isEqualTo("YOUTUBE_CHANNEL");
    }

    @ParameterizedTest
    @ValueSource(strings={"","null","[]","{}","{","{\"kind\":\"OTHER\"}",
            "{\"kind\":\"BOOK\",\"title\":1,\"url\":\"https://fahasa.com\"}",
            "{\"kind\":\"BOOK\",\"title\":\"Demo\",\"url\":1}",
            "{\"kind\":\"BOOK\",\"title\":\"Demo\",\"url\":\"https://fahasa.com\"} {}"})
    void FR012_invalidJsonOrRequiredTypesReturn422(String body) throws Exception {
        fail(post(ROOT).content(body),"VALIDATION_FAILED",null);
        assertThat(audits()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"tags,null","tags,[1]","sortOrder,null","sortOrder,\"1\"","hskMin,\"1\"",
            "hskMin,1.5","isAffiliate,null","isAffiliate,1","publishedYear,9223372036854775808","description,1"})
    void FR012_optionalFieldsAreNotCoercedToAnotherType(String field,String raw) throws Exception {
        ObjectNode body=input("BOOK");
        body.set(field,mapper.readTree(raw));
        fail(post(ROOT).content(body.toString()),"VALIDATION_FAILED",field);
    }

    @Test
    void FR012_FR018_AT09_validatesBoundariesAndRejectsBookValuesOnOtherKinds() throws Exception {
        for(var entry:Map.of("title","😀".repeat(201),"description","a".repeat(2001),"url","a".repeat(2001),
                "thumbnailUrl","a".repeat(2001),"author","a".repeat(201),"publisher","a".repeat(201)).entrySet()) {
            fail(post(ROOT).content(input("BOOK").put(entry.getKey(),entry.getValue()).toString()),"VALIDATION_FAILED",entry.getKey());
        }
        for(var entry:Map.of("hskMin",0,"hskMax",10,"publishedYear",1899).entrySet()) {
            fail(post(ROOT).content(input("BOOK").put(entry.getKey(),entry.getValue()).toString()),"VALIDATION_FAILED",entry.getKey());
        }
        fail(post(ROOT).content(input("BOOK").put("hskMin",3).put("hskMax",1).toString()),"VALIDATION_FAILED","hskMax");
        ObjectNode tags=input("BOOK");
        for(int i=0;i<11;i++) tags.withArray("tags").add("tag"+i);
        fail(post(ROOT).content(tags.toString()),"VALIDATION_FAILED","tags");
        tags=input("BOOK"); tags.putArray("tags").add("😀".repeat(31));
        fail(post(ROOT).content(tags.toString()),"VALIDATION_FAILED","tags");
        for(String field:List.of("author","publisher","publishedYear","isAffiliate")) {
            ObjectNode body=input("PODCAST");
            if(field.equals("publishedYear")) body.put(field,2026);
            else if(field.equals("isAffiliate")) body.put(field,true);
            else body.put(field,"");
            fail(post(ROOT).content(body.toString()),"VALIDATION_FAILED","kind");
        }
        ObjectNode valid=input("BOOK").put("title","😀".repeat(200)).put("hskMin",1).put("hskMax",9).put("publishedYear",2100);
        ok(post(ROOT).content(valid.toString()),201);
    }

    @ParameterizedTest
    @CsvSource({"kind,OTHER","status,OTHER","reported,1","page,-1","page,abc","size,0","size,51"})
    void FR011_invalidFiltersReturn422(String field,String value) throws Exception {
        fail(get(ROOT).param(field,value),"VALIDATION_FAILED",field);
    }

    @ParameterizedTest
    @ValueSource(strings={"0","-1","abc","9223372036854775808","9223372036854775807"})
    void FR012_missingOrInvalidIdsAre404(String id) throws Exception {
        for(String suffix:List.of("","/publish","/hide","/unhide")) {
            MockHttpServletRequestBuilder request=suffix.isEmpty()?get(ROOT+"/"+id):post(ROOT+"/"+id+suffix);
            mvc.perform(editor(request)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error_code").value("REFERENCE_NOT_FOUND"));
        }
        mvc.perform(editor(put(ROOT+"/"+id).content(input("BOOK").toString()))).andExpect(status().isNotFound());
    }

    @Test
    void adminOperationPathsAndStatusesMatchTheSevenLockedContractOperations() throws Exception {
        JsonNode source=new ObjectMapper(new YAMLFactory()).readTree(Files.readString(
                Path.of("specs/018-reference-resources/contracts/openapi-references.yaml")));
        JsonNode runtime=mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        var expected=operations(source);
        assertThat(expected).hasSize(7);
        assertThat(operations(runtime)).isEqualTo(expected);
    }

    private Map<String,Set<String>> operations(JsonNode document) {
        Map<String,Set<String>> result=new HashMap<>();
        document.path("paths").fields().forEachRemaining(path -> {
            if(path.getKey().startsWith(ROOT)) path.getValue().fields().forEachRemaining(operation -> {
                if(Set.of("get","post","put","delete","patch").contains(operation.getKey())) {
                    Set<String> statuses=new HashSet<>(); operation.getValue().path("responses").fieldNames().forEachRemaining(statuses::add);
                    result.put(operation.getKey()+" "+path.getKey(),statuses);
                }
            });
        });
        return result;
    }
    private ObjectNode input(String kind) {
        var body=mapper.createObjectNode().put("kind",kind).put("title","Demo");
        body.put("url",switch(kind){case "BOOK" -> "https://fahasa.com/demo";
            case "PODCAST" -> "https://open.spotify.com/show/demo";default -> "https://youtube.com/@demo";});
        if(kind.equals("BOOK")) body.put("author","Author");
        return body;
    }
    private MockHttpServletRequestBuilder editor(MockHttpServletRequestBuilder request) {
        return request.header("Authorization","Bearer "+token("CONTENT_EDITOR")).contentType(MediaType.APPLICATION_JSON);
    }
    private JsonNode ok(MockHttpServletRequestBuilder request,int status) throws Exception {
        return mapper.readTree(mvc.perform(editor(request)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is(status))
                .andReturn().getResponse().getContentAsByteArray());
    }
    private void fail(MockHttpServletRequestBuilder request,String code,String field) throws Exception {
        var result=mvc.perform(editor(request)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error_code").value(code));
        if(field!=null) result.andExpect(jsonPath("$.details[0].field").value(field));
    }
    private String token(String role) { return jwt.issueAccessToken(editor,Set.of(role),false,UUID.randomUUID()); }
    private long audits() { return jdbc.queryForObject("SELECT count(*) FROM shared.audit_logs WHERE actor_id=?",Long.class,editor); }
    private long seed(String state) {
        return jdbc.queryForObject("INSERT INTO learning.reference_items(kind,title,url,author,status,created_by,published_at) "
                + "VALUES ('BOOK','Demo','https://fahasa.com/demo','Author',?,?,?) RETURNING id",Long.class,state,editor,
                state.equals("PUBLISHED")?Timestamp.from(Instant.parse("2026-11-13T00:00:00Z")):null);
    }
}
