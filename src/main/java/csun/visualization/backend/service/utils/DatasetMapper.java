package csun.visualization.backend.service.utils;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import csun.visualization.backend.domain.DataPoint;
import csun.visualization.backend.domain.Dataset;
import csun.visualization.backend.dto.DataPointDto;
import csun.visualization.backend.dto.DatasetDto;

@Mapper(componentModel = "spring")
public interface DatasetMapper {

    DatasetDto toDto(Dataset entity);
    DataPointDto toDto(DataPoint entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "dataset", ignore = true)
    DataPoint toEntity(DataPointDto dto);

    @AfterMapping
    default void linkChildren(@MappingTarget Dataset dataset) {
        if (dataset.getData() == null) return;
        for (DataPoint p : dataset.getData()) {
            p.setDataset(dataset);
        }
    }
}
