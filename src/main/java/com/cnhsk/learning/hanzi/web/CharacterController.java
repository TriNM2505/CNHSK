package com.cnhsk.learning.hanzi.web;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.learning.hanzi.CharacterQueryService;
import com.cnhsk.learning.hanzi.CharacterQueryService.CharacterDetail;
import com.cnhsk.learning.hanzi.CharacterQueryService.CharacterPage;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/characters")
public class CharacterController {
    private final CharacterQueryService characters;
    private final AuthApi auth;

    public CharacterController(CharacterQueryService characters, AuthApi auth) {
        this.characters = characters;
        this.auth = auth;
    }

    @GetMapping
    public CharacterPage list(@RequestParam(required = false) String hskLevel,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        return characters.list(optionalNumber(hskLevel, "hskLevel"), q,
                number(page, "page"), number(size, "size"));
    }

    @GetMapping("/{id}")
    public CharacterDetail detail(@PathVariable String id) {
        return characters.detail(auth.currentUser().userId(), number(id, "id"));
    }

    private Long optionalNumber(String value, String field) {
        return value == null ? null : number(value, field);
    }

    private long number(String value, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Tham số không hợp lệ.", java.util.Map.of("field", field));
        }
    }
}
