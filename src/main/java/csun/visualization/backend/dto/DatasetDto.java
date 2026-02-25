package csun.visualization.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DatasetDto {

    @NotBlank
    private UUID id;

    @NotBlank
    private String name;

    @NotNull
    private Instant createdAt;

    @NotNull
    private SensorType sensorType;

    @NotNull
    private List<DataPointDto> data;
}
