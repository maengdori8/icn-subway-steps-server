package org.skilltransfer.icnsubway;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 표본 자료(docs/data-model.md). 표가 비어 있을 때만 넣으므로 재시작해도 중복되지 않는다.
 * 경로·단계 문구는 시안 v03의 ROUTES를 옮긴 초안이며 현장·공식 안내로 확인하지 않았다.
 */
@Component
public class SeedData implements ApplicationRunner {
    /** 교재가 정한 로컬 실습용 공개 표본 비밀번호. DB에는 BCrypt 해시만 저장한다. */
    private static final String SAMPLE_PASSWORD = "study-1234";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public SeedData(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (count("route") == 0) seedRoutes();
        if (count("app_user") == 0) seedUsersAndTips();
    }

    private int count(String table) {
        Integer n = jdbc.queryForObject("select count(*) from " + table, Integer.class);
        return n == null ? 0 : n;
    }

    private void seedRoutes() {
        route("EXPRESS", "Express Train", "직통열차", "Non-stop to Seoul Station · ticket needed", "서울역까지 무정차 · 승차권 필요", true, 1);
        route("LOCAL", "All-stop Train", "일반열차", "Stops at every station · T-money card", "모든 역 정차 · 교통카드(T-money)", true, 2);
        route("T2", "T2 → Seoul Station", "T2 → 서울역", "From T2 arrivals", "T2 입국장에서 출발", false, 3);

        // 1·2단계는 두 경로가 같다.
        for (String r : new String[] {"EXPRESS", "LOCAL"}) {
            step(r, 1, "HERE", "PLANE", "arrivals", "제1여객터미널 입국장", "Terminal 1 Arrivals",
                    "Walk out of the arrivals hall", "입국장 밖으로 나오기",
                    "Exit through the arrival doors with your bags.\nLook up for signs that say Airport Railroad ({ko|공항철도}).",
                    "짐을 찾고 도착 출구로 나옵니다.\n천장에서 '공항철도 {en|Airport Railroad}' 표지판을 찾습니다.");
            step(r, 2, "FIND", "ESCALATOR", null, "교통센터 · 공항철도", "Transportation Center · Airport Railroad",
                    "Go down to the Transportation Center", "교통센터로 내려가기",
                    "Follow the Airport Railroad signs to the Transportation Center.\nTake the escalator or elevator down one floor.",
                    "공항철도 표지판을 따라 교통센터로 갑니다.\n에스컬레이터나 엘리베이터로 한 층 내려갑니다.");
        }
        step("EXPRESS", 3, "FIND", "TRAIN", "expressConcourse", "직통열차", "Express Train",
                "Buy an Express Train ticket", "직통열차 승차권 사기",
                "Find the Express Train sign.\nUse a ticket machine or the counter next to it.",
                "'직통열차' 표지판을 찾습니다.\n옆의 발매기나 안내센터에서 승차권을 삽니다.");
        step("EXPRESS", 4, "FIND", "TRAIN", null, "직통열차 승차구", "Express Gate",
                "Pass the Express Train gate", "직통열차 개찰구 통과",
                "Use the gate marked Express Train, not All-stop.\nKeep your ticket until you leave Seoul Station.",
                "'일반열차'가 아닌 '직통열차' 개찰구로 갑니다.\n승차권은 서울역에서 나갈 때까지 보관합니다.");
        step("EXPRESS", 5, "FIND", "TRAIN", null, "직통열차 승강장 · 서울역 방면", "Express Platform · To Seoul Station",
                "Board at the Express platform", "직통열차 승강장에서 탑승",
                "Check the screen says Seoul Station.\nBoard the train and find your seat.",
                "전광판에 '서울역'이 보이는지 확인합니다.\n열차에 타고 자리를 찾습니다.");
        step("LOCAL", 3, "FIND", "TRAIN", "localConcourse", "일반열차", "All-stop Train",
                "Get a T-money card", "교통카드 준비하기",
                "Go to the All-stop Train side.\nBuy or top up a T-money card at the machines.",
                "'일반열차' 쪽으로 갑니다.\n기계에서 교통카드(T-money)를 사거나 충전합니다.");
        step("LOCAL", 4, "FIND", "TRAIN", null, "일반열차 승차구", "All-stop Gate",
                "Tap your card at the gate", "개찰구에 카드 찍기",
                "Tap the T-money card on the reader.\nTap it again when you leave Seoul Station.",
                "단말기에 교통카드를 댑니다.\n서울역에서 나갈 때 한 번 더 찍습니다.");
        step("LOCAL", 5, "FIND", "TRAIN", "localPlatform", "1·2 승강장 · 서울역 방면", "Platform 1·2 · To Seoul Station",
                "Board at platform 1 or 2", "1·2번 승강장에서 탑승",
                "Check the screen says Seoul Station.\nBoard the train heading to Seoul Station.",
                "전광판에 '서울역'이 보이는지 확인합니다.\n서울역 방면 열차에 탑니다.");
    }

    private void seedUsersAndTips() {
        String hash1 = encoder.encode(SAMPLE_PASSWORD);
        String hash2 = encoder.encode(SAMPLE_PASSWORD);
        Instant now = Instant.now();
        jdbc.update("insert into app_user (id, username, password_hash, created_at) values (?, ?, ?, ?)", 101L, "student01", hash1, utc(now));
        jdbc.update("insert into app_user (id, username, password_hash, created_at) values (?, ?, ?, ?)", 102L, "student02", hash2, utc(now));

        tip(7L, "EXPRESS", 3, "Ask at the info center if your card fails", "B1, right of the ticket machines", null,
                "VERIFIED", "2026-10-12T03:00:00Z", 102L, true);
        tip(8L, "LOCAL", 4, "Elevator for big suitcases", "Left end of the gates", "The wide gate next to it fits two suitcases.",
                "OUTDATED", "2026-09-30T03:00:00Z", 101L, false);
    }

    private void route(String code, String nameEn, String nameKo, String summaryEn, String summaryKo, boolean available, int order) {
        jdbc.update("insert into route (code, name_en, name_ko, summary_en, summary_ko, available, sort_order) values (?, ?, ?, ?, ?, ?, ?)",
                code, nameEn, nameKo, summaryEn, summaryKo, available, order);
    }

    private void step(String route, int no, String role, String icon, String photoKey, String signKo, String signEn,
                      String titleEn, String titleKo, String instructionsEn, String instructionsKo) {
        jdbc.update("insert into route_step (route_code, step_no, role, icon, photo_key, sign_ko, sign_en, title_en, title_ko, "
                        + "instructions_en, instructions_ko) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                route, no, role, icon, photoKey, signKo, signEn, titleEn, titleKo, instructionsEn, instructionsKo);
    }

    private void tip(long id, String route, int stepNo, String title, String location, String note, String status,
                     String verifiedAt, long ownerId, boolean example) {
        OffsetDateTime at = utc(Instant.parse(verifiedAt));
        jdbc.update("insert into tip (id, route_code, step_no, title, location, note, status, verified_at, created_at, owner_id, example) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, route, stepNo, title, location, note, status, at, at, ownerId, example);
    }

    private static OffsetDateTime utc(Instant i) {
        return i.atOffset(ZoneOffset.UTC);
    }
}
