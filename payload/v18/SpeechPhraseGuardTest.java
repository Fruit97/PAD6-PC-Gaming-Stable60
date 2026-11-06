package com.example.gamethaitranslator;
public final class SpeechPhraseGuardTest {
    static void same(String a,String b) { if(!a.equals(b)) throw new AssertionError("Expected "+b+" got "+a); }
    static void yes(boolean b) { if(!b) throw new AssertionError("Expected true"); }
    static void no(boolean b) { if(b) throw new AssertionError("Expected false"); }
    public static void main(String[] args) {
        same(SpeechPhraseGuard.completeCandidate("fuck","fuck you"),"fuck you");
        same(SpeechPhraseGuard.completeCandidate("fuck you","fuck"),"fuck you");
        same(SpeechPhraseGuard.completeCandidate("ไอ้ชาติ","ไอ้ชาติหมา"),"ไอ้ชาติหมา");
        same(SpeechPhraseGuard.completeCandidate("hello friend","hello everyone"),"hello friend");
        same(SpeechPhraseGuard.join("fuck","you"),"fuck you");
        same(SpeechPhraseGuard.join("ไอ้ชาติ","หมา"),"ไอ้ชาติหมา");
        same(SpeechPhraseGuard.join("你这","狗东西"),"你这狗东西");
        same(SpeechPhraseGuard.join("hello","guys"),"hello guys");
        same(SpeechPhraseGuard.join("ไป","กินข้าว"),"ไปกินข้าว");
        same(SpeechPhraseGuard.join("fuck you","fuck you"),"fuck you fuck you");
        yes(SpeechPhraseGuard.holdLonger("fuck"));
        yes(SpeechPhraseGuard.holdLonger("you fucking"));
        yes(SpeechPhraseGuard.holdLonger("ไอ้ชาติ"));
        yes(SpeechPhraseGuard.holdLonger("let's go to"));
        no(SpeechPhraseGuard.holdLonger("hello guys"));
        yes(SpeechPhraseGuard.suspiciousLoss("th","zh","ไอ้ชาติหมา","狗"));
        yes(SpeechPhraseGuard.suspiciousLoss("th","en","ไอ้ชาติหมา","Dog"));
        yes(SpeechPhraseGuard.suspiciousLoss("en","th","fuck you","****"));
        yes(SpeechPhraseGuard.suspiciousLoss("en","th","fuck you","fuck"));
        no(SpeechPhraseGuard.suspiciousLoss("th","zh","ไอ้ชาติหมา","你这狗东西！"));
        no(SpeechPhraseGuard.suspiciousLoss("en","th","fuck you","ไปตายซะ ไอ้เหี้ย!"));
        no(SpeechPhraseGuard.suspiciousLoss("th","en","สวัสดี","Hello"));
        no(SpeechPhraseGuard.suspiciousLoss("th","en","ไอ้ชาติหมา","You fucking son of a bitch!"));
        no(SpeechPhraseGuard.suspiciousLoss("th","zh","สวัสดี","你好"));
        System.out.println("SpeechPhraseGuard: 24 checks passed");
    }
}
