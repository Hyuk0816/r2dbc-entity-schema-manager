package io.github.hyuk0816.r2dbc.schema.sample.domain;

import org.springframework.data.annotation.Id;

public class UnannotatedRecord {

    @Id
    private Long id;

    private String value;
}
