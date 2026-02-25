package csun.visualization.backend.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import csun.visualization.backend.domain.Dataset;

public interface DatasetRepository extends JpaRepository<Dataset, UUID> {
}
