package io.github.hyuk0816.r2dbc.schema.sample.domain;

import io.github.hyuk0816.r2dbc.schema.annotation.DdlColumn;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("sample_group")
public class SampleGroup {

    @Id
    private Long id;

    @DdlColumn(type = "varchar", length = 100, nullable = false, defaultValue = "'general'")
    private String groupName;
}
