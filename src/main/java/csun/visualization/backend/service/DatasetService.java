package csun.visualization.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import csun.visualization.backend.dto.DatasetDto;
import csun.visualization.backend.dto.SensorType;

public interface DatasetService {
    DatasetDto uploadDataset(MultipartFile file, SensorType sensorType, String name);

    List<DatasetDto> getAllDatasets();

    DatasetDto getDataset(UUID id);

    void deleteDataset(UUID id);
}
