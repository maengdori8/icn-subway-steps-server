package org.skilltransfer.icnsubway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/**
 * 시드는 표가 비어 있을 때만 한 번 넣는다. 표 정의(시퀀스·열 자료형·길이·UNIQUE·인덱스)가 docs/data-model.md와 같은지
 * 테스트 메모리 DB의 INFORMATION_SCHEMA로 본다. 실행용 data/ 파일 DB에는 접속하지 않는다.
 * 실패하는 INSERT만 시도하므로 다른 테스트가 쓰는 행은 바뀌지 않는다.
 */
class SeedAndSchemaTest extends ApiTestSupport {
    @Autowired SeedData seed;

    @Test
    void seedRunsOnlyWhenTablesAreEmpty() {
        Map<String, Integer> before = counts();
        seed.run(null);
        assertThat(counts()).isEqualTo(before);
        assertThat(before).containsEntry("route", 3).containsEntry("route_step", 10);
        assertThat(jdbc.queryForObject("select count(*) from app_user where id in (101, 102)", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from tip where id in (7, 8)", Integer.class)).isEqualTo(2);
    }

    @Test
    void sequencesStartWhereDataModelSays() {
        var rows = jdbc.queryForList("select sequence_name, cast(start_value as bigint) as start_value, cast(increment as bigint) as inc "
                + "from information_schema.sequences where sequence_schema = 'PUBLIC' and sequence_name in ('APP_USER_SEQ', 'TIP_SEQ') "
                + "order by sequence_name");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("SEQUENCE_NAME", "APP_USER_SEQ").containsEntry("START_VALUE", 103L).containsEntry("INC", 1L);
        assertThat(rows.get(1)).containsEntry("SEQUENCE_NAME", "TIP_SEQ").containsEntry("START_VALUE", 9L).containsEntry("INC", 1L);
    }

    @Test
    void columnTypesAndLengthsMatchDataModel() {
        column("APP_USER", "USERNAME", "CHARACTER VARYING", 24, false);
        column("APP_USER", "PASSWORD_HASH", "CHARACTER VARYING", 60, false);
        column("APP_USER", "CREATED_AT", "TIMESTAMP WITH TIME ZONE", null, false);
        column("ROUTE", "CODE", "CHARACTER VARYING", 16, false);
        column("ROUTE", "SUMMARY_KO", "CHARACTER VARYING", 120, false);
        column("ROUTE_STEP", "PHOTO_KEY", "CHARACTER VARYING", 40, true);
        column("ROUTE_STEP", "INSTRUCTIONS_KO", "CHARACTER VARYING", 1000, false);
        column("ROUTE_STEP", "ELEVATOR_EN", "CHARACTER VARYING", 200, true);
        column("ROUTE_STEP", "ELEVATOR_KO", "CHARACTER VARYING", 200, true);
        // 코드 포인트 80·150자를 담도록 UTF-16 단위로 두 배
        column("TIP", "TITLE", "CHARACTER VARYING", 160, false);
        column("TIP", "LOCATION", "CHARACTER VARYING", 160, false);
        column("TIP", "NOTE", "CHARACTER VARYING", 300, true);
        column("TIP", "VERIFIED_AT", "TIMESTAMP WITH TIME ZONE", null, false);
        column("TIP", "EXAMPLE", "BOOLEAN", null, false);
        // @Enumerated(STRING)은 H2에서 VARCHAR가 아니라 ENUM 열이 된다(@Column length는 쓰이지 않음)
        column("ROUTE_STEP", "ROLE", "ENUM", null, false);
        column("ROUTE_STEP", "ICON", "ENUM", null, false);
        column("TIP", "STATUS", "ENUM", null, false);
        assertThat(enumValues("ROUTE_STEP", "ROLE")).containsExactlyInAnyOrder("HERE", "FIND");
        assertThat(enumValues("ROUTE_STEP", "ICON")).containsExactlyInAnyOrder("PLANE", "ESCALATOR", "TRAIN");
        assertThat(enumValues("TIP", "STATUS")).containsExactlyInAnyOrder("VERIFIED", "OUTDATED");
        // example은 DB 기본값이 없다. false는 서버(Tip 생성자)가 넣는다.
        assertThat(jdbc.queryForObject("select column_default from information_schema.columns "
                + "where table_schema = 'PUBLIC' and table_name = 'TIP' and column_name = 'EXAMPLE'", String.class)).isNull();
    }

    @Test
    void databaseKeepsUniqueKeysIndexesAndEnumValues() {
        // 서비스 검사를 건너뛴 경우(동시 가입, H2 콘솔에서 직접 넣기)에도 DB가 막는다
        assertThatThrownBy(() -> jdbc.update("insert into app_user (id, username, password_hash, created_at) values (9001, 'student01', ?, current_timestamp)",
                "x".repeat(60))).isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> jdbc.update("insert into route_step (route_code, step_no, role, icon, sign_ko, sign_en, title_en, title_ko, "
                + "instructions_en, instructions_ko) values ('EXPRESS', 1, 'HERE', 'PLANE', 'k', 'e', 't', 't', 'i', 'i')"))
                .isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> jdbc.update("insert into tip (id, route_code, step_no, title, location, status, verified_at, created_at, owner_id, example) "
                + "values (9002, 'EXPRESS', 1, 't', 'l', 'DELETED', current_timestamp, current_timestamp, 101, false)"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForList("select column_name from information_schema.index_columns "
                + "where table_schema = 'PUBLIC' and index_name = 'IX_TIP_ROUTE_STEP' order by ordinal_position", String.class))
                .containsExactly("ROUTE_CODE", "STEP_NO");
        assertThat(jdbc.queryForList("select column_name from information_schema.index_columns "
                + "where table_schema = 'PUBLIC' and index_name = 'IX_TIP_OWNER' order by ordinal_position", String.class))
                .containsExactly("OWNER_ID");
        assertThat(jdbc.queryForObject("select count(*) from information_schema.table_constraints "
                + "where table_schema = 'PUBLIC' and constraint_type = 'FOREIGN KEY'", Integer.class)).isEqualTo(3);
    }

    private Map<String, Integer> counts() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String t : List.of("route", "route_step", "app_user", "tip")) {
            m.put(t, jdbc.queryForObject("select count(*) from " + t, Integer.class));
        }
        return m;
    }

    private void column(String table, String column, String type, Integer length, boolean nullable) {
        Map<String, Object> c = jdbc.queryForMap("select data_type, character_maximum_length, is_nullable from information_schema.columns "
                + "where table_schema = 'PUBLIC' and table_name = ? and column_name = ?", table, column);
        String where = table + "." + column;
        assertThat(c.get("DATA_TYPE")).as(where).isEqualTo(type);
        assertThat(c.get("CHARACTER_MAXIMUM_LENGTH") == null ? null : ((Number) c.get("CHARACTER_MAXIMUM_LENGTH")).intValue()).as(where).isEqualTo(length);
        assertThat(c.get("IS_NULLABLE")).as(where).isEqualTo(nullable ? "YES" : "NO");
    }

    private List<String> enumValues(String table, String column) {
        return jdbc.queryForList("select e.value_name from information_schema.columns c join information_schema.enum_values e "
                + "on e.object_schema = c.table_schema and e.object_name = c.table_name and e.object_type = 'TABLE' "
                + "and e.enum_identifier = c.dtd_identifier "
                + "where c.table_schema = 'PUBLIC' and c.table_name = ? and c.column_name = ?", String.class, table, column);
    }
}
