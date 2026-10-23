package org.skilltransfer.icnsubway.repository;

import java.util.List;
import org.skilltransfer.icnsubway.domain.RouteStep;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteStepRepository extends JpaRepository<RouteStep, Long> {
    List<RouteStep> findByRouteCodeOrderByStepNoAsc(String routeCode);
    int countByRouteCode(String routeCode);
}
