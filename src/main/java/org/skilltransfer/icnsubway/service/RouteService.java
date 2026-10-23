package org.skilltransfer.icnsubway.service;

import java.util.List;
import java.util.Optional;
import org.skilltransfer.icnsubway.common.ApiException;
import org.skilltransfer.icnsubway.common.TextRules;
import org.skilltransfer.icnsubway.domain.Route;
import org.skilltransfer.icnsubway.domain.RouteStep;
import org.skilltransfer.icnsubway.repository.RouteRepository;
import org.skilltransfer.icnsubway.repository.RouteStepRepository;
import org.skilltransfer.icnsubway.dto.Responses.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RouteService {
    private final RouteRepository routes;
    private final RouteStepRepository steps;

    public RouteService(RouteRepository routes, RouteStepRepository steps) {
        this.routes = routes;
        this.steps = steps;
    }

    public RouteList list() {
        return new RouteList(routes.findAllByOrderBySortOrderAsc().stream()
                .map(r -> new RouteResponse(r.getCode(), new Text(r.getNameEn(), r.getNameKo()),
                        new Text(r.getSummaryEn(), r.getSummaryKo()), r.isAvailable(), steps.countByRouteCode(r.getCode())))
                .toList());
    }

    /** 없는 경로는 404. 준비 중 경로(T2)는 빈 목록. */
    public StepList steps(String code) {
        if (!routes.existsById(code)) throw ApiException.notFound("경로를 찾을 수 없습니다.");
        return new StepList(code, steps.findByRouteCodeOrderByStepNoAsc(code).stream().map(RouteService::toResponse).toList());
    }

    /** 팁을 받을 수 있는 경로(available)면 그 경로를 준다. */
    Optional<Route> availableRoute(String code) {
        return code == null ? Optional.empty() : routes.findById(code).filter(Route::isAvailable);
    }

    int stepCount(String code) {
        return steps.countByRouteCode(code);
    }

    /** route 항목 오류 문구: 지금 안내 중인 경로 코드를 나열한다. */
    String routeHint() {
        List<String> codes = routes.findAllByOrderBySortOrderAsc().stream().filter(Route::isAvailable).map(Route::getCode).toList();
        return String.join(" 또는 ", codes) + " 중 하나를 보내세요.";
    }

    private static StepResponse toResponse(RouteStep s) {
        return new StepResponse(s.getStepNo(), s.getRole().name(), s.getIcon().name(), s.getPhotoKey(),
                new Sign(s.getSignKo(), s.getSignEn()), new Text(s.getTitleEn(), s.getTitleKo()),
                new Lines(List.of(s.getInstructionsEn().split("\n")), List.of(s.getInstructionsKo().split("\n"))),
                bothOrNull(s.getElevatorEn(), s.getElevatorKo()));
    }

    /**
     * 엘리베이터 위치는 en·ko가 둘 다 있을 때만 준다. 한쪽만 채운 행(null 또는 공백뿐)은 null로 준다.
     * 계약의 Text는 두 언어가 모두 필수라, {en, ko:null}을 보내면 앱이 그 경로의 단계 화면 전체를 읽지 못한다.
     * 공백은 팁 입력과 같은 규칙(TextRules: U+00A0·U+FEFF 등 포함)으로 본다.
     */
    private static Text bothOrNull(String en, String ko) {
        return isEmpty(en) || isEmpty(ko) ? null : new Text(en, ko);
    }

    private static boolean isEmpty(String s) {
        return s == null || TextRules.strip(s).isEmpty();
    }
}
