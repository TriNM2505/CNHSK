package com.cnhsk.auth.controller;

import com.cnhsk.auth.dto.AdminUserDetailResponse;
import com.cnhsk.auth.dto.AdminUserPageResponse;
import com.cnhsk.auth.dto.BanRequest;
import com.cnhsk.auth.dto.GrantRoleRequest;
import com.cnhsk.auth.service.AdminUserQueryService;
import com.cnhsk.auth.service.BanService;
import com.cnhsk.auth.service.RoleService;
import com.cnhsk.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserQueryService adminUserQueryService;
    private final RoleService roleService;
    private final BanService banService;

    public AdminUserController(AdminUserQueryService adminUserQueryService, RoleService roleService,
            BanService banService) {
        this.adminUserQueryService = adminUserQueryService;
        this.roleService = roleService;
        this.banService = banService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Tìm tài khoản — chỉ ADMIN (FR-097)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách tài khoản"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không có quyền"),
            @ApiResponse(responseCode = "422", description = "Tham số tìm kiếm không hợp lệ")
    })
    public AdminUserPageResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return adminUserQueryService.search(q, role, status, page, size);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Chi tiết tài khoản — chỉ ADMIN, ghi VIEW_USER_PII (FR-097)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chi tiết tài khoản"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không có quyền"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy tài khoản"),
            @ApiResponse(responseCode = "500", description = "Lỗi nội bộ")
    })
    public AdminUserDetailResponse detail(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable long id) {
        return adminUserQueryService.getDetail(actor.userId(), id);
    }

    @org.springframework.web.bind.annotation.PostMapping(value = "/{id}/roles",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Gán role cho tài khoản (FR-062→066)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã gán role"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không có quyền hoặc tự cấp role"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy tài khoản"),
            @ApiResponse(responseCode = "409", description = "Role đã tồn tại"),
            @ApiResponse(responseCode = "422", description = "Role không hợp lệ")
    })
    public org.springframework.http.ResponseEntity<Void> grantRole(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable long id,
            @Valid @org.springframework.web.bind.annotation.RequestBody GrantRoleRequest request) {
        roleService.grantRole(actor.userId(), id, request.roleCode());
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}/roles/{roleCode}")
    @Operation(summary = "Thu hồi role khỏi tài khoản (FR-064→066)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã thu hồi role"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không có quyền hoặc tự thu hồi ADMIN"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy tài khoản hoặc role"),
            @ApiResponse(responseCode = "409", description = "Không thể thu hồi ADMIN cuối cùng"),
            @ApiResponse(responseCode = "422", description = "Role không hợp lệ"),
            @ApiResponse(responseCode = "500", description = "Lỗi nội bộ")
    })
    public org.springframework.http.ResponseEntity<Void> revokeRole(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable long id,
            @PathVariable String roleCode) {
        roleService.revokeRole(actor.userId(), id, roleCode);
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/ban", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cấm tài khoản (FR-096)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã cấm"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không thể tự cấm"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy tài khoản"),
            @ApiResponse(responseCode = "409", description = "Không thể cấm ADMIN cuối cùng"),
            @ApiResponse(responseCode = "422", description = "Lý do cấm không hợp lệ"),
            @ApiResponse(responseCode = "500", description = "Lỗi nội bộ")
    })
    public ResponseEntity<Void> ban(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable long id, @Valid @RequestBody BanRequest request) {
        banService.ban(actor.userId(), id, request.reason());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/ban")
    @Operation(summary = "Gỡ cấm tài khoản (FR-096)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Đã gỡ cấm"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Không có quyền"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy tài khoản")
    })
    public ResponseEntity<Void> unban(@AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable long id) {
        banService.unban(actor.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
