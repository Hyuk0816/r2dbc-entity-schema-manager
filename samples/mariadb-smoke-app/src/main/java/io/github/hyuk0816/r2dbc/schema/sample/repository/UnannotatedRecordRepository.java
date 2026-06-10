package io.github.hyuk0816.r2dbc.schema.sample.repository;

import io.github.hyuk0816.r2dbc.schema.sample.domain.UnannotatedRecord;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface UnannotatedRecordRepository extends ReactiveCrudRepository<UnannotatedRecord, Long> {
}
