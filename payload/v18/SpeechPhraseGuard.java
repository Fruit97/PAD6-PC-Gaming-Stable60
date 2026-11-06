package com.example.gamethaitranslator;

import java.util.Locale;

/**
 * Generic integrity guard for speech chunks and translations.
 * This cannot recover inaudible words; it only avoids prematurely sending
 * obvious fragments or severely truncated/masked translations.
 */
public final class SpeechPhraseGuard {
    private SpeechPhraseGuard() {}

    static String completeCandidate(String finalText, String partialText) {
        String f = finalText == null ? "" : finalText.trim();
        String p = partialText == null ? "" : partialText.trim();
        if (f.isEmpty()) return p;
        if (p.isEmpty()) return f;
        // Only reuse a longer partial if the final result is a literal prefix.
        // Never guess additional words that neither hypothesis actually heard.
        if (p.length() > f.length() + 1
                && p.toLowerCase(Locale.ROOT).startsWith(f.toLowerCase(Locale.ROOT))) return p;
        return f;
    }

    static boolean isThai(char c) { return c >= '\u0e00' && c <= '\u0e7f'; }
    static boolean isHan(char c) { return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN; }

    static String join(String existing, String addition) {
        String a = existing == null ? "" : existing.trim();
        String b = addition == null ? "" : addition.trim();
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        // Repeated words can be intentional. Preserve them across final speech sessions.
        char last = a.charAt(a.length()-1);
        char first = b.charAt(0);
        if (last == '.' || last == '!' || last == '?' || last == '。' || last == '！') return a + " " + b;
        // Thai and Chinese are generally written without spaces inside phrases.
        if ((isThai(last) && isThai(first)) || (isHan(last) && isHan(first))) return a + b;
        return a + " " + b;
    }

    static boolean holdLonger(String text) {
        String s = text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
        // Specifically unfinished clauses; not a filter of allowed profanity.
        if (s.matches(".*(?:\\b(?:fuck|fucking|you|your|the|a|an|to|of|with|for|and|but|like|because|at|in|on|is|are|was|were|will|can|do)\\b)$"))
            return true;
        if (s.matches(".*(?:ไอ้|ไอ้ชาติ|ไอ้หมา|ไป|กับ|กำลัง|เพราะ|แล้วก็|ให้|ว่า|ที่|จะ|มึง|ชาติ|狗)$"))
            return true;
        return s.matches(".*(?:你|你的|他妈|你他妈|狗)$");
    }

    static boolean suspiciousLoss(String from, String to, String source, String translated) {
        String original = source == null ? "" : source.trim();
        String result = translated == null ? "" : translated.trim();
        if (original.isEmpty() || result.isEmpty()) return true;
        if (result.matches("(?s).*\\*{2,}.*")) return true;
        int sourceLength = original.codePointCount(0, original.length());
        int targetLength = result.codePointCount(0, result.length());
        if (sourceLength >= 8 && targetLength <= 1) return true;
        // Word-scale mismatch: a full sentence cannot become one expletive word.
        if ("en".equals(to) && sourceLength >= 12 && result.matches("(?i)[a-z]+[!?.]?"))
            return true;
        if ("en".equals(from) && original.matches("(?is).*\\bfuck you\\b.*")
                && (result.equalsIgnoreCase("fuck") || result.equals("操") || result.equals("ฟัค")))
            return true;
        if ("th".equals(from) && original.contains("ชาติหมา")
                && (result.equals("狗") || result.equalsIgnoreCase("dog"))) return true;
        // Do not mistake the bare noun for a full phrase containing modifiers.
        if ("en".equals(from) && original.matches("(?i).*(?:\\bshit\\b|\\basshole\\b).*")
                && result.equalsIgnoreCase("bad")) return true;
        return false;
    }
}
