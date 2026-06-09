package io.github.hyuk0816.r2dbc.schema.sample.domain;

import io.github.hyuk0816.r2dbc.schema.annotation.DdlColumn;
import io.github.hyuk0816.r2dbc.schema.annotation.DdlForeignKey;
import io.github.hyuk0816.r2dbc.schema.annotation.DdlIndex;
import io.github.hyuk0816.r2dbc.schema.annotation.DdlUnique;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("sample_user")
@DdlUnique(name = "uk_sample_user_email", columns = "email")
public class SampleUser {

    @Id
    private Long id;

    @DdlIndex(name = "idx_sample_user_email")
    @DdlColumn(type = "varchar", length = 150, nullable = false, defaultValue = "'anonymous@example.com'")
    private String email;

    @DdlForeignKey(
            name = "fk_sample_user_group_id",
            referencedTable = "sample_group",
            referencedColumn = "id"
    )
    private Long groupId;
}
