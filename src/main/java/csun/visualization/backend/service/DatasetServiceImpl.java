package csun.visualization.backend.service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import csun.visualization.backend.domain.Dataset;
import csun.visualization.backend.dto.DataPointDto;
import csun.visualization.backend.dto.DatasetDto;
import csun.visualization.backend.dto.SensorType;
import csun.visualization.backend.repository.DatasetRepository;
import csun.visualization.backend.service.utils.DatasetFileParser;
import csun.visualization.backend.service.utils.DatasetMapper;
import jakarta.transaction.Transactional;

@Service
@Transactional
public class DatasetServiceImpl implements DatasetService {
    private final DatasetRepository datasetRepository;
    private final DatasetMapper datasetMapper;
    private final DatasetFileParser fileParser;
    private static final long MAX_UPLOAD_BYTES = 75L * 1024 * 1024;
    private static final long MAX_JSON_BYTES = 25L * 1024 * 1024;

    public DatasetServiceImpl(
            DatasetRepository datasetRepository,
            DatasetMapper datasetMapper,
            DatasetFileParser fileParser
    ) {
        this.datasetRepository = datasetRepository;
        this.datasetMapper = datasetMapper;
        this.fileParser = fileParser;
    }

    @Override
    public DatasetDto uploadDataset(MultipartFile file, SensorType sensorType, String name) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File is required");
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Name is required");
        if (sensorType == null) throw new IllegalArgumentException("Sensor type is required");
        if(file.getSize() > MAX_UPLOAD_BYTES) throw new IllegalArgumentException("File too large. Max allowed is 75 MB");

        int maxPoints = computeMaxLimitForHeap();
        List<DataPointDto> points;

        try (
            InputStream stream = file.getInputStream(); 
            BufferedInputStream in = new BufferedInputStream(stream)) {

            DatasetFileParser.Format format = fileParser.checkFormat(in);

            if (format == DatasetFileParser.Format.JSON && file.getSize() > MAX_JSON_BYTES) {
                throw new IllegalArgumentException("JSON file too large. Max allowed is 25MB");
            }
            if (format == DatasetFileParser.Format.CSV && file.getSize() > MAX_UPLOAD_BYTES) {
                throw new IllegalArgumentException("CSV file too large. Max allowed is 75MB");
            }
            points = fileParser.parse(in, format, maxPoints);

        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read uploaded file", e);
        }
        Dataset dataset = Dataset.builder()
                .name(name.trim())
                .createdAt(Instant.now())
                .sensorType(sensorType)
                .data(points.stream().map(datasetMapper::toEntity).toList())
                .build();

        dataset.getData().forEach(p -> p.setDataset(dataset));
        Dataset saved = datasetRepository.save(dataset);
        return datasetMapper.toDto(saved);
    }

    @Override
    public DatasetDto getDataset(UUID datasetId) {
        Dataset ds = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new IllegalArgumentException("Dataset not found: " + datasetId));
        return datasetMapper.toDto(ds);
    }

    @Override
    public List<DatasetDto> getAllDatasets() {
        return datasetRepository.findAll().stream()
                .map(datasetMapper::toDto)
                .toList();
    }

    @Override
    public void deleteDataset(UUID datasetId) {
        if (datasetId == null) throw new IllegalArgumentException("Dataset ID is required");
        datasetRepository.deleteById(datasetId);
    }

    /**
     * Calculates the maximum Java heap memory runtime limit
     * 
     * @return the maximum Java heap memory runtime limit
     */
    private int computeMaxLimitForHeap() {
        // get the max memory in bytes and convert to MB
        long maxHeapBytes = Runtime.getRuntime().maxMemory();
        long maxHeapMb = maxHeapBytes / (1024 * 1024);

        if (maxHeapMb <= 512)  return 100_000;
        if (maxHeapMb <= 1024) return 250_000;
        if (maxHeapMb <= 2048) return 600_000;
        return 1_000_000;
    }
}
