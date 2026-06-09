package io.github.hyuk0816.r2dbc.schema.sample.repository;

import io.github.hyuk0816.r2dbc.schema.sample.domain.SampleGroup;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface SampleGroupRepository extends ReactiveCrudRepository<SampleGroup, Long> {
}
