package org.grubhart.pucp.tesis.module_domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChangeLeadTimeRepository extends JpaRepository<ChangeLeadTime, Long> {
}
