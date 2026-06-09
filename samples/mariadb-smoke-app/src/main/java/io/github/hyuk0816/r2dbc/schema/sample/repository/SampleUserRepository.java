package io.github.hyuk0816.r2dbc.schema.sample.repository;

import io.github.hyuk0816.r2dbc.schema.sample.domain.SampleUser;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface SampleUserRepository extends ReactiveCrudRepository<SampleUser, Long> {
}
