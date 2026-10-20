package org.skilltransfer.icnsubway;

/** 입력 글자 규칙: 앞뒤 유니코드 공백 제거, 길이는 코드 포인트(이모지 1개 = 1자)로 센다. */
final class TextRules {
    private TextRules() {}

    /** null이면 null. U+00A0·U+3000 같은 공백 문자와 U+FEFF도 공백으로 본다(String.strip은 U+00A0을 남긴다). */
    static String strip(String s) {
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

    static int length(String s) {
        return s.codePointCount(0, s.length());
    }

    private static boolean isSpace(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp) || cp == 0xFEFF;
    }
}
