package csun.visualization.backend.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DataPointDto {
    @NotNull
    private Instant timestamp;

    @NotNull
    private Double x;

    @NotNull
    private Double y;

    @NotNull
    private Double z;
}
