package com.cube.nanotimer.util.usage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONObject;
import org.junit.Test;

public class UsageReportTest {

  private static Map<String, String> votes() {
    Map<String, String> votes = new LinkedHashMap<String, String>();
    votes.put("pll_t", "R U R' U' R' F R2 U' R' U' R U R' F'");
    votes.put("oll_27", "R U R' U R U2 R'");
    return votes;
  }

  @Test
  public void bodyCarriesVersionAndVotesSortedByCase() throws Exception {
    String body = UsageReport.of(votes()).getBody();
    assertEquals("{\"usage_version\":1,\"votes\":{\"oll_27\":\"R U R' U R U2 R'\","
        + "\"pll_t\":\"R U R' U' R' F R2 U' R' U' R U R' F'\"}}", body);
    JSONObject read = new JSONObject(body);
    assertEquals(1, read.getInt("usage_version"));
    assertEquals("R U R' U R U2 R'", read.getJSONObject("votes").getString("oll_27"));
  }

  @Test
  public void requestHashIsHexSha256OfTheExactBodyBytes() throws Exception {
    UsageReport report = UsageReport.of(votes());
    byte[] digest = MessageDigest.getInstance("SHA-256")
        .digest(report.getBody().getBytes(StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder();
    for (byte b : digest) {
      hex.append(String.format("%02x", b));
    }
    assertEquals(hex.toString(), report.requestHash());
    assertEquals(64, report.requestHash().length());
  }

  @Test
  public void requestHashOfAKnownBody() {
    // sha256sum of {"usage_version":1,"votes":{}}: lowercase hex, leading zeros kept
    assertEquals("d6c54c6d36fc0f9329d19a12ad1d919ebb62af8fdd20266499044a184cfbd16c",
        UsageReport.of(Collections.<String, String>emptyMap()).requestHash());
  }

  @Test
  public void atMostMaxVotesASend() {
    Map<String, String> many = new TreeMap<String, String>();
    for (int i = 0; i < UsageReport.MAX_VOTES + 20; i++) {
      many.put(String.format("pair_%03d", i), "R U R'");
    }
    Map<String, String> sent = UsageReport.of(many).getVotes();
    assertEquals(UsageReport.MAX_VOTES, sent.size());
    assertTrue(sent.containsKey("pair_000"));
    assertFalse(sent.containsKey(String.format("pair_%03d", UsageReport.MAX_VOTES)));
  }

  @Test
  public void acceptedMarksOnlyTheTakenCases() {
    UsageReport report = UsageReport.of(votes());
    UsageReport.Answer answer =
        report.read(202, "{\"status\":\"received\",\"taken\":[\"oll_27\",\"pll_x\"]}");
    assertEquals(UsageReport.Kind.TAKEN, answer.getKind());
    assertEquals(Collections.singletonMap("oll_27", "R U R' U R U2 R'"), answer.getTaken());
  }

  @Test
  public void anUnreadableAcceptMarksNothing() {
    UsageReport.Answer answer = UsageReport.of(votes()).read(202, "not json");
    assertEquals(UsageReport.Kind.TAKEN, answer.getKind());
    assertTrue(answer.getTaken().isEmpty());
  }

  @Test
  public void integrityFailedDropsTheSend() {
    UsageReport.Answer answer = UsageReport.of(votes()).read(403,
        "{\"error\":{\"code\":\"integrity_failed\",\"message\":\"x\"}}");
    assertEquals(UsageReport.Kind.DROPPED, answer.getKind());
    assertTrue(answer.getTaken().isEmpty());
  }

  @Test
  public void limitsAndServiceTroubleAreRetried() {
    UsageReport report = UsageReport.of(votes());
    assertEquals(UsageReport.Kind.RETRY, report.read(429, null).getKind());
    assertEquals(UsageReport.Kind.RETRY, report.read(503,
        "{\"error\":{\"code\":\"integrity_unavailable\"}}").getKind());
    assertEquals(UsageReport.Kind.RETRY, report.read(500, null).getKind());
  }

  @Test
  public void anInvalidBodyIsDroppedNotRetried() {
    assertEquals(UsageReport.Kind.DROPPED, UsageReport.of(votes()).read(400, null).getKind());
    assertEquals(UsageReport.Kind.DROPPED, UsageReport.of(votes()).read(413, null).getKind());
  }

  @Test
  public void forgetIsDoneOnceTakenOrRefusedForGood() {
    assertTrue(UsageReport.forgetDone(202));
    assertTrue(UsageReport.forgetDone(400));
    assertFalse(UsageReport.forgetDone(429));
    assertFalse(UsageReport.forgetDone(503));
  }
}
