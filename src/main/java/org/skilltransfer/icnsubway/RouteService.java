package org.skilltransfer.icnsubway;

import java.util.List;
import java.util.Optional;
import org.skilltransfer.icnsubway.Responses.*;
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
        Text elevator = s.getElevatorEn() == null && s.getElevatorKo() == null ? null : new Text(s.getElevatorEn(), s.getElevatorKo());
        return new StepResponse(s.getStepNo(), s.getRole().name(), s.getIcon().name(), s.getPhotoKey(),
                new Sign(s.getSignKo(), s.getSignEn()), new Text(s.getTitleEn(), s.getTitleKo()),
                new Lines(List.of(s.getInstructionsEn().split("\n")), List.of(s.getInstructionsKo().split("\n"))),
                elevator);
    }
}
