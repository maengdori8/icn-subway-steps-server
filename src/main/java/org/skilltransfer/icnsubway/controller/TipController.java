package org.skilltransfer.icnsubway.controller;

import java.net.URI;
import org.skilltransfer.icnsubway.security.AuthUser;
import org.skilltransfer.icnsubway.service.TipService;
import org.skilltransfer.icnsubway.dto.Requests.TipCreateRequest;
import org.skilltransfer.icnsubway.dto.Requests.TipStatusRequest;
import org.skilltransfer.icnsubway.dto.Responses.TipList;
import org.skilltransfer.icnsubway.dto.Responses.TipResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** S02 팁 영역, S03 팁 등록, S06 내 팁. 등록·상태 변경·내 팁은 SecurityConfig가 인증을 요구한다. */
@RestController
public class TipController {
    private final TipService tips;

    public TipController(TipService tips) {
        this.tips = tips;
    }

    @GetMapping("/api/tips")
    public TipList list(@RequestParam(required = false) String route, @RequestParam(required = false) String step,
                        @AuthenticationPrincipal AuthUser user) {
        return tips.listForStep(route, step, user == null ? null : user.id());
    }

    @GetMapping("/api/tips/{id}")
    public TipResponse get(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return tips.get(id, user == null ? null : user.id());
    }

    @PostMapping("/api/tips")
    public ResponseEntity<TipResponse> create(@RequestBody TipCreateRequest body, @AuthenticationPrincipal AuthUser user) {
        TipResponse created = tips.create(body, user);
        return ResponseEntity.created(URI.create("/api/tips/" + created.id())).body(created);
    }

    @PatchMapping("/api/tips/{id}/status")
    public TipResponse changeStatus(@PathVariable long id, @RequestBody TipStatusRequest body,
                                    @AuthenticationPrincipal AuthUser user) {
        return tips.changeStatus(id, body, user);
    }

    @GetMapping("/api/me/tips")
    public TipList mine(@AuthenticationPrincipal AuthUser user) {
        return tips.mine(user.id());
    }
}
