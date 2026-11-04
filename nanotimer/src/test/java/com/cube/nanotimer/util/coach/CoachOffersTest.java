package com.cube.nanotimer.util.coach;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Which of Play's offers a base plan is bought with. */
public class CoachOffersTest {

  private static CoachOffers.Offer offer(String basePlan, String offerId, String... tags) {
    return new CoachOffers.Offer(basePlan, offerId, Arrays.asList(tags),
        basePlan + "/" + offerId);
  }

  /** As Play lists them to somebody who never had the subscription. */
  private static List<CoachOffers.Offer> newBuyer() {
    List<CoachOffers.Offer> offers = new ArrayList<CoachOffers.Offer>();
    offers.add(offer("monthly", "trial", "trial"));
    offers.add(offer("monthly", "launch", "launch"));
    offers.add(offer("monthly", null));
    offers.add(offer("yearly", "trial", "trial"));
    offers.add(offer("yearly", "launch", "launch"));
    offers.add(offer("yearly", null));
    return offers;
  }

  @Test
  public void theLaunchOfferIsTakenWhilePlayListsIt() {
    Assert.assertEquals("yearly/launch",
        CoachOffers.pick(newBuyer(), "yearly").getOfferToken());
    Assert.assertEquals("monthly/launch",
        CoachOffers.pick(newBuyer(), "monthly").getOfferToken());
  }

  /** The launch offer switched off in Play Console, with no release. */
  @Test
  public void theTrialIsTakenOnceTheLaunchOfferIsGone() {
    List<CoachOffers.Offer> offers = new ArrayList<CoachOffers.Offer>();
    offers.add(offer("monthly", null));
    offers.add(offer("monthly", "trial", "trial"));
    Assert.assertEquals("monthly/trial", CoachOffers.pick(offers, "monthly").getOfferToken());
  }

  /** Somebody who had the subscription before is listed neither offer. */
  @Test
  public void thePlainBasePlanIsTakenWhenNoOfferIsListed() {
    List<CoachOffers.Offer> offers = Arrays.asList(offer("monthly", null), offer("yearly", null));
    CoachOffers.Offer picked = CoachOffers.pick(offers, "yearly");
    Assert.assertEquals("yearly/null", picked.getOfferToken());
    Assert.assertNull(picked.getOfferId());
  }

  /** An offer inherits its base plan's tags, and so does the plain entry. */
  @Test
  public void aTagOnTheBasePlanItselfIsNotAnOffer() {
    List<CoachOffers.Offer> offers = Arrays.asList(offer("monthly", null, "launch"),
        offer("monthly", "trial", "launch", "trial"));
    Assert.assertEquals("monthly/trial", CoachOffers.pick(offers, "monthly").getOfferToken());
  }

  @Test
  public void anOfferOnTheOtherBasePlanIsNotTaken() {
    List<CoachOffers.Offer> offers = Arrays.asList(offer("yearly", "launch", "launch"),
        offer("monthly", null));
    Assert.assertNull(CoachOffers.pick(offers, "monthly").getOfferId());
  }

  /** As Play lists them once the members' offer exists: to everybody, its eligibility being ours. */
  private static List<CoachOffers.Offer> withMembersOffer(String... tags) {
    List<CoachOffers.Offer> offers = newBuyer();
    offers.add(0, offer("monthly", "post-coach", tags));
    return offers;
  }

  @Test
  public void aMemberTakesTheMembersOfferInsteadOfTheLaunchOne() {
    Assert.assertEquals("monthly/post-coach",
        CoachOffers.pick(withMembersOffer("beta-thanks"), "monthly", true).getOfferToken());
  }

  @Test
  public void anyoneElseNeverTakesTheMembersOffer() {
    Assert.assertEquals("monthly/launch",
        CoachOffers.pick(withMembersOffer("beta-thanks"), "monthly", false).getOfferToken());
    // Even tagged as the launch offer by mistake, or with no tag at all.
    Assert.assertEquals("monthly/launch",
        CoachOffers.pick(withMembersOffer("launch"), "monthly", false).getOfferToken());
  }

  @Test
  public void theMembersOfferIsKnownByItsIdWithoutTheTag() {
    Assert.assertEquals("monthly/post-coach",
        CoachOffers.pick(withMembersOffer(), "monthly", true).getOfferToken());
  }

  /** It is set on the monthly plan only: a member buying yearly gets what everybody gets. */
  @Test
  public void aMemberBuyingYearlyTakesTheLaunchOffer() {
    Assert.assertEquals("yearly/launch",
        CoachOffers.pick(withMembersOffer("beta-thanks"), "yearly", true).getOfferToken());
  }

  /** As set in Play Console: half the monthly price, paid twice, then the monthly price. */
  @Test
  public void theMembersOfferIsHalfOffForTwoMonths() {
    CoachOffers.Phase half = new CoachOffers.Phase("€2.50", 2_500_000, "P1M", 2);
    CoachOffers.Offer offer = new CoachOffers.Offer("monthly", "post-coach",
        Collections.<String>emptyList(), "token",
        Arrays.asList(half, new CoachOffers.Phase("€4.99", 4_990_000, "P1M", 0)));
    Assert.assertSame(half, offer.getIntro());
    Assert.assertTrue(half.isRepeated());
    Assert.assertEquals(2, half.getSpan().getCount());
    Assert.assertEquals('M', half.getSpan().getUnit());
    Assert.assertEquals(1, half.getPeriod().getCount());
    Assert.assertEquals(50, offer.getPercentOff());
    Assert.assertFalse(offer.getRegular().isRepeated());
  }

  @Test
  public void aBasePlanPlayDoesNotListCannotBeBought() {
    Assert.assertNull(CoachOffers.pick(newBuyer(), "weekly"));
    Assert.assertNull(CoachOffers.pick(Collections.<CoachOffers.Offer>emptyList(), "monthly"));
  }

  private static CoachOffers.Offer priced(CoachOffers.Phase... phases) {
    return new CoachOffers.Offer("yearly", "offer", Collections.<String>emptyList(), "token",
        Arrays.asList(phases));
  }

  private static final CoachOffers.Phase FREE = new CoachOffers.Phase("Free", 0, "P2W");
  private static final CoachOffers.Phase LOWER = new CoachOffers.Phase("€43.99", 43_990_000, "P1Y");
  private static final CoachOffers.Phase YEAR = new CoachOffers.Phase("€54.99", 54_990_000, "P1Y");

  /** The launch offer as it is set in Play Console: two free weeks, a first year at 20% off. */
  @Test
  public void theLaunchOfferIsFreeThenLowerThenTheBasePlan() {
    CoachOffers.Offer launch = priced(FREE, LOWER, YEAR);
    Assert.assertEquals(2, launch.getFreePeriod().getCount());
    Assert.assertEquals('W', launch.getFreePeriod().getUnit());
    Assert.assertSame(LOWER, launch.getIntro());
    Assert.assertSame(YEAR, launch.getRegular());
    Assert.assertEquals(20, launch.getPercentOff());
  }

  @Test
  public void theTrialTakesNothingOff() {
    CoachOffers.Offer trial = priced(FREE, YEAR);
    Assert.assertNotNull(trial.getFreePeriod());
    Assert.assertNull(trial.getIntro());
    Assert.assertEquals(0, trial.getPercentOff());
  }

  @Test
  public void thePlainBasePlanIsItsPriceAlone() {
    CoachOffers.Offer plain = priced(YEAR);
    Assert.assertNull(plain.getFreePeriod());
    Assert.assertNull(plain.getIntro());
    Assert.assertSame(YEAR, plain.getRegular());
  }

  /** Three months at a lower monthly price against a yearly one compares nothing. */
  @Test
  public void aLowerPriceOverAnotherLengthGivesNoPercentage() {
    CoachOffers.Offer offer = priced(new CoachOffers.Phase("€9.99", 9_990_000, "P3M"), YEAR);
    Assert.assertNotNull(offer.getIntro());
    Assert.assertEquals(0, offer.getPercentOff());
  }

  @Test
  public void aLengthPlayWritesAnotherWayIsNotRead() {
    Assert.assertNull(CoachOffers.Period.parse("P1M2D"));
    Assert.assertNull(CoachOffers.Period.parse("PT5M"));
    Assert.assertNull(CoachOffers.Period.parse(null));
    Assert.assertEquals(14, CoachOffers.Period.parse("P14D").getCount());
  }
}
