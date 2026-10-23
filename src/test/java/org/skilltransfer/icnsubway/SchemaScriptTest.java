package org.skilltransfer.icnsubway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * docs/schema.sql을 만드는 방법이자, 그 파일이 지금 엔티티로 만든 create 스크립트와 같은지 보는 검사
 * (실행 설정 ddl-auto=update가 새 DB에 보내는 DDL과는 열 순서·UNIQUE 방식이 다르다. docs/schema.sql 머리말).
 * 이 테스트에서만 Hibernate 스키마 스크립트 생성(jakarta.persistence.schema-generation.scripts.*)을 켜서
 * build/schema/create.sql에 CREATE 문을 쓴다. 앱 실행 설정(application*.properties)에는 이 설정이 없다.
 * 메모리 DB 이름을 따로 써서 다른 테스트의 DB와 섞이지 않고, 실행용 data/ 파일 DB에는 접속하지 않는다.
 * 실행: .\gradlew.bat test --tests "*SchemaScriptTest"
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:icn-schema;DB_CLOSE_DELAY=-1",
        // 스크립트 설정이 있으면 Hibernate가 ddl-auto를 보지 않으므로, 메모리 DB에 표를 만드는 동작도 JPA 설정으로 준다.
        "spring.jpa.properties.jakarta.persistence.schema-generation.database.action=drop-and-create",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create",
        "spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=" + SchemaScriptTest.SCRIPT,
        "spring.jpa.properties.hibernate.hbm2ddl.schema-generation.script.append=false",
        "spring.jpa.properties.hibernate.hbm2ddl.delimiter=;",
        "spring.jpa.properties.hibernate.format_sql=true"})
@ActiveProfiles("test")
class SchemaScriptTest {
    static final String SCRIPT = "build/schema/create.sql";

    static {
        // Spring(=Hibernate)이 뜨기 전에 예전 실행이 남긴 파일을 지우고 폴더를 만든다.
        // 지우지 않으면 스크립트 생성이 멈춰도 옛 파일로 비교해 통과할 수 있다.
        try {
            Files.deleteIfExists(Path.of(SCRIPT));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        new File(SCRIPT).getParentFile().mkdirs();
    }

    @Test
    void docsSchemaSqlMatchesWhatHibernateCreates() throws Exception {
        assertThat(Path.of(SCRIPT)).as("이번 실행에서 Hibernate가 스크립트를 새로 써야 한다").exists();
        Set<String> generated = statements(Files.readString(Path.of(SCRIPT)));
        assertThat(generated).contains(
                "create sequence app_user_seq start with 103 increment by 1",
                "create sequence tip_seq start with 9 increment by 1");
        Set<String> documented = statements(Files.readString(Path.of("docs/schema.sql")));
        assertThat(documented)
                .as("엔티티가 바뀌었으면 build/schema/create.sql을 보고 docs/schema.sql을 다시 정리한다")
                .isEqualTo(generated);
    }

    /**
     * '--' 주석을 지우고 ';'로 나눈 뒤 공백을 한 칸으로 줄이고 괄호·쉼표 둘레 공백을 없앤 문장 집합.
     * 순서·줄바꿈·들여쓰기·주석은 비교하지 않는다. 대소문자는 그대로 비교한다('VERIFIED' 같은 ENUM 값과 FK 이름이 글자까지 같아야 한다).
     */
    static Set<String> statements(String sql) {
        String noComments = sql.lines().map(l -> l.replaceFirst("--.*$", "")).collect(Collectors.joining("\n"));
        return Arrays.stream(noComments.split(";"))
                .map(s -> s.replaceAll("\\s+", " ").replaceAll(" ?([(),]) ?", "$1").trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void comparisonKeepsCaseOfEnumValues() {
        assertThat(statements("create table t (s enum ('VERIFIED') not null);"))
                .isNotEqualTo(statements("create table t (s enum ('verified') not null);"))
                .isEqualTo(statements("-- 주석\ncreate table t (\n    s enum ('VERIFIED') not null\n);\n"));
    }
}
