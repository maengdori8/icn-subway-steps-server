package org.skilltransfer.icnsubway.repository;

import java.util.List;
import org.skilltransfer.icnsubway.domain.Route;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<Route, String> {
    List<Route> findAllByOrderBySortOrderAsc();
}
