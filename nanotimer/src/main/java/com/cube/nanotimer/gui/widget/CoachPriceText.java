package com.cube.nanotimer.gui.widget;

import android.content.res.Resources;

import com.cube.nanotimer.R;
import com.cube.nanotimer.util.coach.CoachOffers;

import java.util.List;

/**
 * What Play asks for a base plan, in the words the Plan tab and the purchase sheet show. Every
 * price is Play's own text for the buyer's country, and every length is read off the offer, so an
 * offer changed in Play Console changes what is said here with no release.
 */
final class CoachPriceText {

  private final Resources resources;

  CoachPriceText(Resources resources) {
    this.resources = resources;
  }

  /** The tile's big figure: the first price paid, the offer's lower one before the base plan's. */
  String price(CoachOffers.Offer offer) {
    CoachOffers.Phase intro = offer.getIntro();
    return (intro != null ? intro : offer.getRegular()).getPrice();
  }

  /** What the big figure pays for: "first year", or "a year" with no lower price; null if unsaid. */
  String per(CoachOffers.Offer offer) {
    CoachOffers.Phase intro = offer.getIntro();
    CoachOffers.Period period = (intro != null ? intro : offer.getRegular()).getPeriod();
    if (period == null) {
      return null;
    }
    if (intro != null && intro.isRepeated()) {
      // "a month for 2 months"
      return resources.getString(R.string.coach_price_first, each(period),
          length(intro.getSpan()));
    }
    boolean one = period.getCount() == 1;
    if (one && period.getUnit() == 'Y') {
      return resources.getString(intro != null ? R.string.coach_tile_first_year
          : R.string.coach_tile_year);
    }
    if (one && period.getUnit() == 'M') {
      return resources.getString(intro != null ? R.string.coach_tile_first_month
          : R.string.coach_tile_month);
    }
    return resources.getString(intro != null ? R.string.coach_tile_first
        : R.string.coach_tile_every, length(period));
  }

  /** "then €54.99 a year", or null for an offer with no lower price before it. */
  String then(CoachOffers.Offer offer) {
    return offer.getIntro() == null ? null
        : resources.getString(R.string.coach_tile_then, regular(offer));
  }

  /** "Free for 2 weeks", or null for an offer with no free period. */
  String free(CoachOffers.Offer offer) {
    String free = length(offer.getFreePeriod());
    return free == null ? null : resources.getString(R.string.coach_purchase_free, free);
  }

  /** "20% off", or null for an offer that takes nothing off. */
  String tag(CoachOffers.Offer offer) {
    int off = offer.getPercentOff();
    return off == 0 ? null : resources.getString(R.string.coach_price_off, off);
  }

  /** The button that buys the plan: its free weeks when it has some, else its price. */
  String button(CoachOffers.Offer offer) {
    String free = length(offer.getFreePeriod());
    return free == null
        ? resources.getString(R.string.coach_purchase_subscribe_for, paidFirst(offer))
        : resources.getString(R.string.coach_purchase_start_free, free);
  }

  /** The button on the Plan tab that opens the sheet. */
  String offerButton(List<CoachOffers.Offer> plans) {
    for (CoachOffers.Offer plan : plans) {
      String free = length(plan.getFreePeriod());
      if (free != null) {
        return resources.getString(R.string.coach_offer_try_free, free);
      }
    }
    return resources.getString(R.string.coach_offer_try);
  }

  /**
   * The line under that button: what both base plans cost once any offer is over, the shorter one
   * first. Null unless Play listed both.
   *
   * @param plans the yearly one first
   */
  String offerNote(List<CoachOffers.Offer> plans) {
    if (plans.size() != 2) {
      return null;
    }
    String yearly = regular(plans.get(0));
    String monthly = regular(plans.get(1));
    boolean free = false;
    int off = plans.get(0).getPercentOff();
    for (CoachOffers.Offer plan : plans) {
      free |= length(plan.getFreePeriod()) != null;
      if (plan.getPercentOff() != off) {
        off = 0; // one figure would be wrong for the other plan, so none is said
      }
    }
    if (!free) {
      return resources.getString(R.string.coach_offer_prices, monthly, yearly);
    }
    return off == 0 ? resources.getString(R.string.coach_offer_then, monthly, yearly)
        : resources.getString(R.string.coach_offer_then_less, monthly, yearly, off);
  }

  /** "€54.99 a year": what the base plan costs once any offer is over. */
  private String regular(CoachOffers.Offer offer) {
    return perPeriod(offer.getRegular());
  }

  /** "a month", or "every 3 months" when the period isn't one month or one year. */
  private String each(CoachOffers.Period period) {
    if (period.getCount() == 1 && period.getUnit() == 'Y') {
      return resources.getString(R.string.coach_tile_year);
    }
    if (period.getCount() == 1 && period.getUnit() == 'M') {
      return resources.getString(R.string.coach_tile_month);
    }
    return resources.getString(R.string.coach_tile_every, length(period));
  }

  /** "€4.99 a month": a price with what each payment covers. */
  private String perPeriod(CoachOffers.Phase phase) {
    CoachOffers.Period period = phase.getPeriod();
    if (period == null) {
      return phase.getPrice();
    }
    if (period.getCount() == 1 && period.getUnit() == 'Y') {
      return resources.getString(R.string.coach_price_yearly, phase.getPrice());
    }
    if (period.getCount() == 1 && period.getUnit() == 'M') {
      return resources.getString(R.string.coach_price_monthly, phase.getPrice());
    }
    return resources.getString(R.string.coach_price_every, phase.getPrice(), length(period));
  }

  /** The first price that is not free: the offer's lower one, else the base plan's own. */
  private String paidFirst(CoachOffers.Offer offer) {
    CoachOffers.Phase intro = offer.getIntro();
    if (intro == null) {
      return regular(offer);
    }
    CoachOffers.Period period = intro.getPeriod();
    if (period == null) {
      return intro.getPrice();
    }
    if (intro.isRepeated()) {
      // "€2.50 a month for 2 months"
      return resources.getString(R.string.coach_price_first, perPeriod(intro),
          length(intro.getSpan()));
    }
    if (period.getCount() == 1 && period.getUnit() == 'Y') {
      return resources.getString(R.string.coach_price_first_year, intro.getPrice());
    }
    if (period.getCount() == 1 && period.getUnit() == 'M') {
      return resources.getString(R.string.coach_price_first_month, intro.getPrice());
    }
    return resources.getString(R.string.coach_price_first, intro.getPrice(), length(period));
  }

  /** "2 weeks", or null for no period. */
  private String length(CoachOffers.Period period) {
    if (period == null) {
      return null;
    }
    int plural;
    switch (period.getUnit()) {
      case 'D':
        plural = R.plurals.coach_period_days;
        break;
      case 'W':
        plural = R.plurals.coach_period_weeks;
        break;
      case 'M':
        plural = R.plurals.coach_period_months;
        break;
      default:
        plural = R.plurals.coach_period_years;
        break;
    }
    return resources.getQuantityString(plural, period.getCount(), period.getCount());
  }
}
