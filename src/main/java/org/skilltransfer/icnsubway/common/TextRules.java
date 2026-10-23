package org.skilltransfer.icnsubway.common;

/** 입력 글자 규칙: 앞뒤 유니코드 공백 제거, 길이는 코드 포인트(이모지 1개 = 1자)로 센다. */
public final class TextRules {
    private TextRules() {}

    /** null이면 null. U+00A0·U+3000 같은 공백 문자와 U+FEFF도 공백으로 본다(String.strip은 U+00A0·U+FEFF를 남긴다). */
    public static String strip(String s) {
        if (s == null) return null;
        int start = 0, end = s.length();
        while (start < end) {
            int cp = s.codePointAt(start);
            if (!isSpace(cp)) break;
            start += Character.charCount(cp);
        }
        while (end > start) {
            int cp = s.codePointBefore(end);
            if (!isSpace(cp)) break;
            end -= Character.charCount(cp);
        }
        return s.substring(start, end);
    }

    public static int length(String s) {
        return s.codePointCount(0, s.length());
    }

    /**
     * 유니코드 White_Space(U+0009~U+000D, U+0085, 공백·줄·문단 구분 문자 Zs·Zl·Zp)와 U+FEFF.
     * 앱(Dart String.trim)과 같은 범위다. Character.isWhitespace는 U+0085를 빼고 U+001C~U+001F를 넣어서 쓰지 않는다.
     */
    private static boolean isSpace(int cp) {
        return (cp >= 0x09 && cp <= 0x0D) || cp == 0x85 || Character.isSpaceChar(cp) || cp == 0xFEFF;
    }
}
