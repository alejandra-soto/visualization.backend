package csun.visualization.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import csun.visualization.backend.dto.DatasetDto;
import csun.visualization.backend.dto.SensorType;
import csun.visualization.backend.service.DatasetService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;

    @PostMapping(value="/upload", consumes= MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DatasetDto> uploadDataset(
            @RequestParam @NotNull MultipartFile file,
            @RequestParam @NotNull SensorType sensorType,
            @RequestParam @NotNull String name
    ) {
        DatasetDto dto = datasetService.uploadDataset(file, sensorType, name);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    public ResponseEntity<List<DatasetDto>> getAllDatasets() {
        return ResponseEntity.status(HttpStatus.OK).body(datasetService.getAllDatasets());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DatasetDto> getDataset(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(datasetService.getDataset(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDataset(@PathVariable UUID id) {
        datasetService.deleteDataset(id);
        return ResponseEntity.noContent().build();
    }
}