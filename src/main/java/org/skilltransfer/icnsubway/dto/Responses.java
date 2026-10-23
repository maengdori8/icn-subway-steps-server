package org.skilltransfer.icnsubway.dto;

import java.time.Instant;
import java.util.List;

/** 응답 JSON. 비밀번호 해시 같은 내부 값은 담지 않는다. */
public final class Responses {
    private Responses() {}

    public record Text(String en, String ko) {}

    public record Sign(String ko, String en) {}

    public record Lines(List<String> en, List<String> ko) {}

    public record RouteResponse(String code, Text name, Text summary, boolean available, int stepCount) {}

    public record RouteList(List<RouteResponse> routes) {}

    public record StepResponse(int stepNo, String role, String icon, String photoKey, Sign sign,
                               Text title, Lines instructions, Text elevator) {}

    public record StepList(String route, List<StepResponse> steps) {}

    public record TipResponse(long id, String route, int stepNo, String title, String location, String note,
                              String status, Instant verifiedAt, Instant createdAt, long ownerId,
                              boolean mine, boolean example) {}

    public record TipList(List<TipResponse> tips) {}

    public record UserResponse(long id, String username) {}

    public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {}
}
