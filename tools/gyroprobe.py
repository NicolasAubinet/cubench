#!/usr/bin/env python3
"""Measure a smart cube's gyro stream from a GyroProbe logcat capture.

The pose smoothing in live.html and the still-detection the straighten button waits on are both
shaped around two numbers - how often the cube reports, and how much its reading wobbles while the
cube is held still. Both were only ever measured on a MoYu V10. This reads a capture and prints
them for whatever cube produced it.

Capture with GanCube.CAPTURE = true and SmartCubeManager.GYRO_PROBE = true, then:

    adb logcat -c && adb logcat -s GyroProbe:I > gyroprobe-gan.log

Lines it reads (anything else is ignored, so a raw logcat file is fine):

    G <hostMs> <w> <x> <y> <z>          one gyro sample, as it arrived
    A <hostMs> <movedDeg> still=<bool> timedOut=<bool>   one tick of the straighten wait
    ANCHOR <hostMs> via=<still|timeout> <w> <x> <y> <z>  what that wait settled on
    PRESS <hostMs>                      the straighten button

Usage:  python tools/gyroprobe.py gyroprobe-gan.log
"""

import argparse
import math
import re
import sys

# Two readings this far apart or more are a turn, not sensor noise. Used to pick the quiet
# stretches out of a capture that also contains deliberate movement.
MOVING_DEGREES = 2.0

# The app's own constants, copied so the report can say whether this cube trips them.
# Keep them in step with SmartCubeManager: a stale value here reports headroom that the
# real straighten button does not have.
STILL_POLL_MS = 200
STILL_DEGREES = 8.0

SAMPLE = re.compile(r"\bG (\d+) (-?[\d.eE+-]+) (-?[\d.eE+-]+) (-?[\d.eE+-]+) (-?[\d.eE+-]+)")
TICK = re.compile(r"\bA (\d+) (-?[\d.eE+-]+) still=(\w+) timedOut=(\w+)")
ANCHOR = re.compile(r"\bANCHOR (\d+) via=(\w+)")
PRESS = re.compile(r"\bPRESS (\d+)")


def angle_between(a, b):
    """Angle in degrees between two orientation quaternions, taking the short way round."""
    dot = sum(x * y for x, y in zip(a, b))
    return 2.0 * math.degrees(math.acos(max(0.0, min(1.0, abs(dot)))))


def percentile(values, fraction):
    if not values:
        return float("nan")
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int(fraction * len(ordered)))]


def describe(label, values, unit):
    if not values:
        print(f"  {label}: no data")
        return
    print(f"  {label}: median {percentile(values, .5):.2f}{unit}  "
          f"p95 {percentile(values, .95):.2f}{unit}  max {max(values):.2f}{unit}")


def parse(path):
    samples, ticks, anchors, presses = [], [], [], []
    with open(path, encoding="utf-8", errors="replace") as handle:
        for line in handle:
            match = SAMPLE.search(line)
            if match:
                samples.append((int(match.group(1)),
                                tuple(float(match.group(i)) for i in (2, 3, 4, 5))))
                continue
            match = TICK.search(line)
            if match:
                ticks.append((int(match.group(1)), float(match.group(2)),
                              match.group(3) == "true", match.group(4) == "true"))
                continue
            match = ANCHOR.search(line)
            if match:
                anchors.append((int(match.group(1)), match.group(2)))
                continue
            match = PRESS.search(line)
            if match:
                presses.append(int(match.group(1)))
    return samples, ticks, anchors, presses


def report_rate(samples):
    print(f"\nSTREAM  {len(samples)} samples over "
          f"{(samples[-1][0] - samples[0][0]) / 1000.0:.1f}s")
    gaps = [b[0] - a[0] for a, b in zip(samples, samples[1:])]
    gaps = [g for g in gaps if g > 0]
    if not gaps:
        print("  every sample shares a timestamp - cannot measure a rate")
        return None
    median = percentile(gaps, .5)
    describe("interval", gaps, "ms")
    print(f"  implied rate: {1000.0 / median:.1f} Hz (from the median interval)")

    # The trap the still-detection is built around: a poll faster than the cube reports reads the
    # same sample twice, and two identical readings look like perfect stillness.
    identical = sum(1 for a, b in zip(samples, samples[1:]) if a[1] == b[1])
    print(f"  identical consecutive samples: {identical} "
          f"({100.0 * identical / max(1, len(samples) - 1):.1f}%)")
    if median >= STILL_POLL_MS:
        print(f"  !! STILL_POLL_MS is {STILL_POLL_MS}ms and this cube reports every {median:.0f}ms. "
              f"Consecutive ticks CAN read the same sample, which reads as perfect stillness.")
    elif median > STILL_POLL_MS / 2:
        print(f"  .. STILL_POLL_MS is {STILL_POLL_MS}ms and assumes two periods per tick; this cube "
              f"gives {STILL_POLL_MS / median:.1f}. The margin is gone but duplicates are unlikely.")
    return median


def report_noise(samples):
    """Noise floor, measured over the quietest stretch of the capture."""
    steps = [(a[0], angle_between(a[1], b[1])) for a, b in zip(samples, samples[1:])]
    if not steps:
        return
    quiet = [s for s in steps if s[1] < MOVING_DEGREES]
    print(f"\nNOISE   {len(quiet)} of {len(steps)} steps are under {MOVING_DEGREES}deg "
          f"(treated as the cube sitting still)")
    describe("step while still", [s[1] for s in quiet], "deg")

    # What the straighten button actually asks: did it move less than STILL_DEGREES between two
    # ticks STILL_POLL_MS apart? Re-created here from the raw stream.
    # A stretch counts as still only if EVERY step inside it is small. Rejecting on the endpoints
    # alone would silently cap the answer at whatever cutoff was used - the first version of this
    # did exactly that and reported its own filter ceiling as the measurement.
    ordered = sorted(samples)
    drifts = []
    ahead = 0
    for index, (at, quat) in enumerate(ordered):
        ahead = max(ahead, index)
        while ahead < len(ordered) and ordered[ahead][0] < at + STILL_POLL_MS:
            ahead += 1
        if ahead >= len(ordered):
            break
        if any(angle_between(ordered[k][1], ordered[k + 1][1]) > MOVING_DEGREES
               for k in range(index, ahead)):
            continue  # the cube was being turned across this stretch
        drifts.append(angle_between(quat, ordered[ahead][1]))
    if not drifts:
        print(f"  no stretch of {STILL_POLL_MS}ms was quiet enough to measure a drift over")
        return
    describe(f"drift over {STILL_POLL_MS}ms while still", drifts, "deg")
    headroom = STILL_DEGREES - max(drifts)
    print(f"  headroom to STILL_DEGREES ({STILL_DEGREES}deg): {headroom:.2f}deg")
    if headroom <= 0:
        print(f"  !! This cube drifts more than {STILL_DEGREES}deg over a tick while sitting still, "
              f"so the wait never reads still and always falls out on the timeout.")
    elif headroom < STILL_DEGREES * 0.25:
        print(f"  .. Under a quarter of the threshold is left. A still cube will sometimes fail to "
              f"read still and fall through to the timeout.")


def report_anchors(ticks, anchors, presses):
    print(f"\nSTRAIGHTEN  {len(presses)} press(es), {len(anchors)} anchor(s)")
    for at, via in anchors:
        print(f"  anchored at {at} via {via}")
    by_timeout = sum(1 for _, via in anchors if via == "timeout")
    if anchors and by_timeout:
        print(f"  !! {by_timeout} of {len(anchors)} anchored on the TIMEOUT, i.e. on whatever the "
              f"cube happened to read, not on a cube confirmed still.")
    instant = [t for t in ticks if t[2] and t[1] == 0.0]
    if instant:
        print(f"  !! {len(instant)} tick(s) read EXACTLY 0.00deg of movement - that is the same "
              f"sample read twice, not a cube held still.")


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("log", help="logcat capture containing GyroProbe lines")
    args = parser.parse_args()

    samples, ticks, anchors, presses = parse(args.log)
    if not samples:
        sys.exit("No 'G' sample lines found. Is GanCube.CAPTURE on, and did the cube move at all?")

    report_rate(samples)
    report_noise(samples)
    if ticks or presses:
        report_anchors(ticks, anchors, presses)
    else:
        print("\nSTRAIGHTEN  no tick lines - SmartCubeManager.GYRO_PROBE was off")


if __name__ == "__main__":
    main()
