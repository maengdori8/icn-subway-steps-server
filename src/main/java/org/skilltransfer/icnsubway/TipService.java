package org.skilltransfer.icnsubway;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.skilltransfer.icnsubway.Requests.TipCreateRequest;
import org.skilltransfer.icnsubway.Requests.TipStatusRequest;
import org.skilltransfer.icnsubway.Responses.TipList;
import org.skilltransfer.icnsubway.Responses.TipResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팁 조회·등록·상태 변경. 올린 사람은 토큰의 사용자로 정하고, 상태 변경은 DB의 owner_id와 같을 때만 한다.
 * 판단 순서: (401은 보안 필터) → 400 입력 → 404 팁 없음 → 403 올린 사람 아님 → 변경.
 */
@Service
public class TipService {
    static final int TITLE_MAX = 80;
    static final int LOCATION_MAX = 80;
    static final int NOTE_MAX = 150;

    private final TipRepository tips;
    private final UserRepository users;
    private final RouteService routes;

    public TipService(TipRepository tips, UserRepository users, RouteService routes) {
        this.tips = tips;
        this.users = users;
        this.routes = routes;
    }

    @Transactional(readOnly = true)
    public TipList listForStep(String route, String step, Long viewerId) {
        Map<String, String> errors = new LinkedHashMap<>();
        Route r = checkRoute(route, errors);
        Integer stepNo = null;
        if (step == null || step.isBlank()) {
            errors.put("step", "단계 번호를 보내세요.");
        } else {
            try {
                stepNo = Integer.valueOf(step.trim());
            } catch (NumberFormatException e) {
                errors.put("step", "단계 번호는 숫자로 보내세요.");
            }
        }
        if (r != null && stepNo != null) checkStepRange(r, stepNo, "step", errors);
        if (!errors.isEmpty()) throw ApiException.validation(errors);
        return new TipList(tips.findByRouteCodeAndStepNoOrderByIdDesc(r.getCode(), stepNo).stream()
                .map(t -> toResponse(t, viewerId)).toList());
    }

    @Transactional(readOnly = true)
    public TipResponse get(long id, Long viewerId) {
        return toResponse(find(id), viewerId);
    }

    @Transactional(readOnly = true)
    public TipList mine(long userId) {
        return new TipList(tips.findByOwnerIdOrderByIdDesc(userId).stream().map(t -> toResponse(t, userId)).toList());
    }

    @Transactional
    public TipResponse create(TipCreateRequest req, AuthUser user) {
        Map<String, String> errors = new LinkedHashMap<>();
        Route r = checkRoute(req.route(), errors);
        if (req.stepNo() == null) errors.put("stepNo", "단계 번호를 보내세요.");
        else if (r != null) checkStepRange(r, req.stepNo(), "stepNo", errors);
        String title = required(req.title(), TITLE_MAX, "title", errors);
        String location = required(req.location(), LOCATION_MAX, "location", errors);
        String note = TextRules.strip(req.note());
        if (note != null && note.isEmpty()) note = null;
        if (note != null && TextRules.length(note) > NOTE_MAX) errors.put("note", NOTE_MAX + "자 이하로 입력하세요.");
        if (!errors.isEmpty()) throw ApiException.validation(errors);

        Tip tip = new Tip(r, req.stepNo(), title, location, note, users.getReferenceById(user.id()), now());
        return toResponse(tips.save(tip), user.id());
    }

    @Transactional
    public TipResponse changeStatus(long id, TipStatusRequest req, AuthUser user) {
        Tip.Status status = parseStatus(req.status());
        Tip tip = find(id);
        if (tip.getOwner().getId() != user.id()) {
            throw ApiException.forbidden("이 팁을 올린 사람만 상태를 바꿀 수 있습니다.");
        }
        tip.changeStatus(status, now());
        return toResponse(tip, user.id());
    }

    private Tip find(long id) {
        return tips.findById(id).orElseThrow(() -> ApiException.notFound("팁을 찾을 수 없습니다."));
    }

    private Route checkRoute(String code, Map<String, String> errors) {
        Route r = routes.availableRoute(code).orElse(null);
        if (r == null) errors.put("route", routes.routeHint());
        return r;
    }

    private void checkStepRange(Route r, int stepNo, String field, Map<String, String> errors) {
        int count = routes.stepCount(r.getCode());
        if (stepNo < 1 || stepNo > count) errors.put(field, "1~" + count + " 사이의 단계 번호를 보내세요.");
    }

    /** 앞뒤 공백 제거 뒤 1~max 코드 포인트. 오류면 errors에 S03 문구를 넣는다. */
    private static String required(String raw, int max, String field, Map<String, String> errors) {
        String v = TextRules.strip(raw);
        if (v == null || v.isEmpty()) errors.put(field, "1~" + max + "자로 입력하세요.");
        else if (TextRules.length(v) > max) errors.put(field, max + "자 이하로 입력하세요.");
        return v;
    }

    private static Tip.Status parseStatus(String raw) {
        if (raw != null) {
            for (Tip.Status s : Tip.Status.values()) if (s.name().equals(raw)) return s;
        }
        throw ApiException.validation(Map.of("status", "VERIFIED 또는 OUTDATED 중 하나를 보내세요."));
    }

    /** JSON과 DB(마이크로초)에서 같은 값이 보이도록 밀리초로 자른다. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static TipResponse toResponse(Tip t, Long viewerId) {
        long ownerId = t.getOwner().getId();
        return new TipResponse(t.getId(), t.getRoute().getCode(), t.getStepNo(), t.getTitle(), t.getLocation(), t.getNote(),
                t.getStatus().name(), t.getVerifiedAt(), t.getCreatedAt(), ownerId,
                viewerId != null && viewerId == ownerId, t.isExample());
    }
}
