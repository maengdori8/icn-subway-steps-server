package org.skilltransfer.icnsubway;

/**
 * 요청 본문. 여기 없는 항목(status, ownerId, verifiedAt, id …)이 오면 Jackson이 거절하고 400이 된다
 * (spring.jackson.deserialization.fail-on-unknown-properties=true).
 */
public final class Requests {
    private Requests() {}

    public record TipCreateRequest(String route, Integer stepNo, String title, String location, String note) {}

    public record TipStatusRequest(String status) {}

    public record SignupRequest(String username, String password) {}

    public record LoginRequest(String username, String password) {}
}
