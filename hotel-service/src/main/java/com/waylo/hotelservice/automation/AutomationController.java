package com.waylo.hotelservice.automation;

import com.waylo.hotelservice.automation.AutomationDtos.AutomationOverview;
import com.waylo.hotelservice.automation.AutomationDtos.InventoryRuleRequest;
import com.waylo.hotelservice.automation.AutomationDtos.InventoryRuleResult;
import com.waylo.hotelservice.automation.AutomationDtos.RateRuleRequest;
import com.waylo.hotelservice.automation.AutomationDtos.RateRuleResult;
import com.waylo.hotelservice.automation.AutomationDtos.ResetRequest;
import com.waylo.hotelservice.dto.ApiResponse;
import com.waylo.hotelservice.security.HotelAccessGuard;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/hotels/{hotelId}/automation")
public class AutomationController {

    private final AutomationService service;
    private final HotelAccessGuard accessGuard;

    public AutomationController(AutomationService service, HotelAccessGuard accessGuard) {
        this.service = service;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AutomationOverview>> overview(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId) {
        accessGuard.requireRoomManage(userId, hotelId);
        return ResponseEntity.ok(ApiResponse.of(service.overview(hotelId)));
    }

    @PutMapping("/inventory-rules/{roomTypeId}")
    public ResponseEntity<ApiResponse<InventoryRuleResult>> saveInventoryRule(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId,
            @PathVariable UUID roomTypeId,
            @Valid @RequestBody InventoryRuleRequest request) {
        accessGuard.requireRoomManage(userId, hotelId);
        return ResponseEntity.ok(ApiResponse.of(service.saveInventoryRule(hotelId, roomTypeId, request)));
    }

    @PostMapping("/rate-rules")
    public ResponseEntity<ApiResponse<RateRuleResult>> createRateRule(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId,
            @Valid @RequestBody RateRuleRequest request) {
        accessGuard.requireRoomManage(userId, hotelId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.createRateRule(hotelId, request)));
    }

    @PutMapping("/rate-rules/{ruleId}")
    public ResponseEntity<ApiResponse<RateRuleResult>> updateRateRule(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId,
            @PathVariable UUID ruleId,
            @Valid @RequestBody RateRuleRequest request) {
        accessGuard.requireRoomManage(userId, hotelId);
        return ResponseEntity.ok(ApiResponse.of(service.updateRateRule(hotelId, ruleId, request)));
    }

    @DeleteMapping("/rate-rules/{ruleId}")
    public ResponseEntity<Void> deleteRateRule(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId,
            @PathVariable UUID ruleId) {
        accessGuard.requireRoomManage(userId, hotelId);
        service.deleteRateRule(hotelId, ruleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<AutomationSummary>> apply(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId) {
        accessGuard.requireRoomManage(userId, hotelId);
        return ResponseEntity.ok(ApiResponse.of(service.apply(hotelId)));
    }

    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<AutomationSummary>> reset(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID hotelId,
            @Valid @RequestBody ResetRequest request) {
        accessGuard.requireRoomManage(userId, hotelId);
        return ResponseEntity.ok(ApiResponse.of(service.reset(hotelId, request)));
    }
}
