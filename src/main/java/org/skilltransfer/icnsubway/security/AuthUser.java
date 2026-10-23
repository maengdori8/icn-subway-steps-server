package org.skilltransfer.icnsubway.security;

/** 검증한 토큰의 사용자. 컨트롤러에서 @AuthenticationPrincipal로 받는다(익명이면 null). */
public record AuthUser(long id, String username) {}
