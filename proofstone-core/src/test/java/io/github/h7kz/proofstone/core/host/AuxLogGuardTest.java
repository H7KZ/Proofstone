package io.github.h7kz.proofstone.core.host;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The stand-in and driver host log guard (T-M10-06), judged from scripted logs. No Docker. */
class AuxLogGuardTest {

    private static String check(List<String> lines, boolean refusalExpected) {
        return AuxLogGuard.violations("stand-in", lines, Map.of(), refusalExpected);
    }

    @Test
    void aCleanStandInLogPasses() {
        assertThat(check(List.of("STUB ready: dns 53, tls 443, address 172.18.0.2",
                "DNS A client.discovery.minecraft-services.net", "STUB answered client.discovery.minecraft-services.net",
                "STUB GET client.discovery.minecraft-services.net/discovery"), false)).isNull();
    }

    @Test
    void anErrorLineFails() {
        assertThat(check(List.of("2026-11-17 12:00:00 ERROR something broke"), false)).contains("1 unexpected")
                .contains("ERROR something broke");
        assertThat(check(List.of("[12:00:00 ERROR]: boom"), false)).contains("boom");
    }

    @Test
    void anUncaughtExceptionFails() {
        assertThat(check(List.of("Exception in thread \"main\" java.lang.IllegalStateException: x"), false))
                .contains("Exception in thread");
        assertThat(check(List.of("java.net.BindException: Address already in use"), false)).contains("BindException");
        assertThat(check(List.of("Caused by: java.io.IOException: closed"), false)).contains("IOException");
    }

    @Test
    void aStackFrameAloneAndWordsContainingErrorAreNotOffending() {
        assertThat(check(List.of("\tat java.base/java.lang.Thread.run(Thread.java:1583)", "DNS error receiving: x",
                "ERRORS are counted", "NO_ERROR"), false)).isNull();
    }

    @Test
    void aRefusedLineFailsExceptInTheStandInAbsentFault() {
        List<String> log = List.of("STUB refused client.discovery.minecraft-services.net");

        assertThat(check(log, false)).contains("STUB refused");
        assertThat(check(log, true)).isNull();
    }

    @Test
    void theStandInAbsentFaultStillFailsOnAnError() {
        assertThat(check(List.of("STUB refused x", "ERROR real"), true)).contains("1 unexpected").contains("ERROR real");
    }

    @Test
    void anAllowlistedLineIsSkippedAndAnyOtherStillFails() {
        Map<Pattern, String> allow = Map.of(Pattern.compile("ERROR known noise"), "seen in run N");

        assertThat(AuxLogGuard.violations("driver host", List.of("ERROR known noise"), allow, false)).isNull();
        assertThat(AuxLogGuard.violations("driver host", List.of("ERROR known noise", "ERROR new"), allow, false))
                .contains("driver host").contains("ERROR new").doesNotContain("known noise");
    }

    @Test
    void theDriverHostReadyLineIsClean() {
        assertThat(AuxLogGuard.violations("driver host", List.of("bedrock driver host ready: RPC v1, Bedrock protocol 2225"),
                AuxLogGuard.DRIVER_HOST_ALLOWLIST, false)).isNull();
    }
}
