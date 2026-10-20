package org.skilltransfer.icnsubway;

import org.skilltransfer.icnsubway.Responses.RouteList;
import org.skilltransfer.icnsubway.Responses.StepList;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** S01 경로 목록, S02 단계 안내. 로그인 없이 본다. */
@RestController
public class RouteController {
    private final RouteService routes;

    public RouteController(RouteService routes) {
        this.routes = routes;
    }

    @GetMapping("/api/routes")
    public RouteList list() {
        return routes.list();
    }

    @GetMapping("/api/routes/{route}/steps")
    public StepList steps(@PathVariable String route) {
        return routes.steps(route);
    }
}
