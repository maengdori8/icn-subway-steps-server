package org.skilltransfer.icnsubway;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteStepRepository extends JpaRepository<RouteStep, Long> {
    List<RouteStep> findByRouteCodeOrderByStepNoAsc(String routeCode);
    int countByRouteCode(String routeCode);
}
