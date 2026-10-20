package org.skilltransfer.icnsubway;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<Route, String> {
    List<Route> findAllByOrderBySortOrderAsc();
}
