package com.cube.nanotimer.util.coach;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which of Play's offers a purchase of a base plan is made with. Offers are known by the tag set
 * on them in Play Console, never by their id, so the launch offer can be ended there with no
 * release: Play stops listing it and the next tag is taken.
 */
public final class CoachOffers {

  /** In the order they are wanted. Play lists an offer only to a buyer who may have it. */
  static final String[] TAGS = {"launch", "trial"};

  /**
   * The thanks to beta members, on the monthly plan. Play lists it to everybody (its eligibility is
   * the developer's), so it is taken only for a member, and then instead of any other offer.
   */
  static final String MEMBERS_TAG = "beta-thanks";
  static final String MEMBERS_OFFER_ID = "post-coach";

  /** A length of time as Play writes one: a count of days, weeks, months or years. */
  public static final class Period {
    private static final Pattern ISO = Pattern.compile("P(\\d+)([DWMY])");

    private final int count;
    private final char unit;

    private Period(int count, char unit) {
      this.count = count;
      this.unit = unit;
    }

    /** @return null for text that is not one count of one unit, which Play Console cannot set */
    static Period parse(String iso) {
      Matcher matcher = ISO.matcher(iso == null ? "" : iso);
      return matcher.matches()
          ? new Period(Integer.parseInt(matcher.group(1)), matcher.group(2).charAt(0)) : null;
    }

    public int getCount() {
      return count;
    }

    /** D, W, M or Y. */
    public char getUnit() {
      return unit;
    }
  }

  /** One stretch of what an offer charges: a price, and how long each payment of it covers. */
  public static final class Phase {
    private final String price;
    private final long micros;
    private final Period period;
    private final int cycles;

    /**
     * @param price as Play formats it for the buyer's country
     * @param period as Play writes it, such as P1M
     * @param cycles how many times it is paid, 0 or 1 for once or for good
     */
    Phase(String price, long micros, String period, int cycles) {
      this.price = price;
      this.micros = micros;
      this.period = Period.parse(period);
      this.cycles = Math.max(cycles, 1);
    }

    Phase(String price, long micros, String period) {
      this(price, micros, period, 1);
    }

    public String getPrice() {
      return price;
    }

    /** What one payment covers. Null when Play wrote a length this app does not read. */
    public Period getPeriod() {
      return period;
    }

    /** How long the whole phase lasts: two months for a monthly price paid twice. */
    public Period getSpan() {
      return period == null ? null : new Period(period.count * cycles, period.unit);
    }

    /** Whether it is paid more than once, so its price is per payment, not for the span. */
    public boolean isRepeated() {
      return cycles > 1;
    }
  }

  /** One way of buying a base plan, as Play lists it. */
  public static final class Offer {
    private final String basePlan;
    private final String offerId;
    private final List<String> tags;
    private final String offerToken;
    private final List<Phase> phases;

    /**
     * @param offerId null for the base plan bought plainly, with no offer
     * @param phases what is charged, in order, the base plan's own price last
     */
    Offer(String basePlan, String offerId, List<String> tags, String offerToken,
        List<Phase> phases) {
      this.basePlan = basePlan;
      this.offerId = offerId;
      this.tags = tags;
      this.offerToken = offerToken;
      this.phases = phases;
    }

    Offer(String basePlan, String offerId, List<String> tags, String offerToken) {
      this(basePlan, offerId, tags, offerToken, Collections.<Phase>emptyList());
    }

    public String getBasePlan() {
      return basePlan;
    }

    String getOfferId() {
      return offerId;
    }

    /** What the purchase is launched with. */
    String getOfferToken() {
      return offerToken;
    }

    /** How long the offer is free for before anything is charged, null when it is not. */
    public Period getFreePeriod() {
      return phases.size() > 1 && phases.get(0).micros == 0 ? phases.get(0).period : null;
    }

    /** The lower price paid before the base plan's own, null when the offer has none. */
    public Phase getIntro() {
      for (Phase phase : phases.subList(0, Math.max(phases.size() - 1, 0))) {
        if (phase.micros > 0) {
          return phase;
        }
      }
      return null;
    }

    /** What the base plan costs once any offer is over, null when Play listed no price. */
    public Phase getRegular() {
      return phases.isEmpty() ? null : phases.get(phases.size() - 1);
    }

    /**
     * How much less the lower price is, in percent. 0 when there is none, or when it is paid over
     * another length of time, which makes the two prices no comparison.
     */
    public int getPercentOff() {
      Phase intro = getIntro();
      Phase regular = getRegular();
      if (intro == null || regular.micros <= 0 || intro.period == null || regular.period == null
          || intro.period.count != regular.period.count
          || intro.period.unit != regular.period.unit) {
        return 0;
      }
      return Math.max(0, Math.round(100 - 100f * intro.micros / regular.micros));
    }
  }

  private CoachOffers() {
  }

  static Offer pick(List<Offer> offers, String basePlan) {
    return pick(offers, basePlan, false);
  }

  /**
   * The offer to buy the base plan with: for a member, the members' offer when Play lists it on
   * this base plan; else the first wanted tag Play lists for it, else the plain base plan, else
   * null when Play lists no way of buying it.
   */
  static Offer pick(List<Offer> offers, String basePlan, boolean member) {
    if (member) {
      for (Offer offer : offers) {
        if (offer.basePlan.equals(basePlan) && forMembers(offer)) {
          return offer;
        }
      }
    }
    for (String tag : TAGS) {
      for (Offer offer : offers) {
        // A base plan's own tags are listed on its plain entry too, which is no offer.
        if (offer.basePlan.equals(basePlan) && offer.offerId != null
            && offer.tags.contains(tag) && !forMembers(offer)) {
          return offer;
        }
      }
    }
    for (Offer offer : offers) {
      if (offer.basePlan.equals(basePlan) && offer.offerId == null) {
        return offer;
      }
    }
    return null;
  }

  /** Known by its tag or its id, so a tag forgotten in Play Console never opens it to everybody. */
  private static boolean forMembers(Offer offer) {
    return offer.offerId != null
        && (offer.tags.contains(MEMBERS_TAG) || MEMBERS_OFFER_ID.equals(offer.offerId));
  }
}
