package org.skilltransfer.icnsubway;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipRepository extends JpaRepository<Tip, Long> {
    List<Tip> findByRouteCodeAndStepNoOrderByIdDesc(String routeCode, int stepNo);
    List<Tip> findByOwnerIdOrderByIdDesc(Long ownerId);
}
