package com.cube.nanotimer.cube;

import android.util.Log;
import android.view.View;
import android.view.ViewStub;
import android.webkit.WebView;

import com.cube.nanotimer.R;
import com.cube.nanotimer.smartcube.cube.CubieCube;
import com.cube.nanotimer.smartcube.model.CubeConnection;
import com.cube.nanotimer.smartcube.model.CubeConnectionListener;
import com.cube.nanotimer.smartcube.model.CubeMove;
import com.cube.nanotimer.smartcube.model.CubeMoveListener;
import com.cube.nanotimer.smartcube.model.CubeState;
import com.cube.nanotimer.smartcube.model.CubeStateListener;

/**
 * The connected smart cube, mirrored on screen as it is turned: a 3D cube in a WebView, fed the
 * move stream and the physical orientation.
 *
 * <p>Inflated from a {@link ViewStub} only once a cube is connected, so a user without one pays
 * neither the WebView nor the ~1 MB cubing.js parse — the same rule the app-bar chip follows.
 * Bind once, then {@link #start()} / {@link #stop()} from the activity's resume/pause and
 * {@link #destroy()} from its onDestroy.
 *
 * <p>On the timer it draws in the gap under the scramble, in place of the scramble's state diagram:
 * a cube that is following the scramble move by move needs no picture of it, and the two at once
 * left both of them too small to read. The screen owns that swap, not this class — see
 * {@code TimerActivity.refreshStatePreviewOwner}.
 *
 * <p><b>⚠️ The pose is the RAW gyro reading, and a slice must not be taken out of it.</b> The cube
 * reports a slice as its two opposite faces and nothing else, so the state drawn here is the one the
 * core sees — and the core is where the gyro sits. Drawing a core-frame state at the core's own pose
 * is what makes the two agree: {@code pair · spin = slice} is satisfied by the pose itself. Taking
 * the spin out was tried, and it is a real correction to the wrong quantity — it recovers the
 * <em>shell's</em> pose (measured over the {@code roux140} capture's 19 confirmed slices: 7.1° of
 * residual against 92.8° uncorrected), which would only pair with a shell-frame state, which this
 * is not.
 *
 * <p><b>The cube on screen is pointed at states, not walked to them</b> ({@link VirtualCube}), so
 * there is no state it cannot show: a cube scrambled before it was connected is drawn where it
 * really is, and a turn gone missing is corrected on the next state the cube reports rather than
 * marked as out of sync and left wrong until the next solve. The twin below is what notices, since
 * the cube sends its whole state after every turn.
 */
public class LiveCubeView implements CubeConnectionListener, CubeMoveListener, CubeStateListener,
    VirtualCube.ReadyListener {

  /**
   * Past this many turns since the last state, the cube is pointed at the twin's state again.
   *
   * <p>The animated alg grows for as long as the cube goes unsolved, and everything the player
   * derives is derived over the whole of it, so a session spent turning without ever solving would
   * grow it without bound. Nothing moves on screen: the state either side of the re-point is the
   * same state.
   */
  private static final int RESEED_AFTER_MOVES = 60;

  /** The panels around it fade over this when the screen stands down for a solve; this goes too. */
  private static final long FADE_MS = 120;

  private final View.OnTouchListener touchListener;

  private ViewStub stub;
  private View cubeLayout;
  private View veil;
  private View cubeWeb;
  private VirtualCube cube;
  private boolean obscured;
  private boolean suppressed;

  /** The same cube, turned in Java: what the page is showing, and what a lost move is caught by. */
  private final CubieCube twin = new CubieCube();
  private int movesSinceState;

  /**
   * Whether the cube on screen has ever been pointed at a state the physical one was really in.
   *
   * <p>Until it has there is nothing to draw, so the mirror stays off the screen rather than
   * showing a solved cube that nobody solved.
   */
  private boolean seeded;

  /**
   * @param touchListener the timer screen's own, forwarded so the cube is not a dead zone —
   *     {@code CLAUDE.md} requires a tap anywhere in the timer to start or stop it, and a WebView
   *     swallows presses. May be null outside the timer.
   */
  public LiveCubeView(View.OnTouchListener touchListener) {
    this.touchListener = touchListener;
  }

  /**
   * Points the cube at a freshly laid-out screen. Safe to call again, and must be: the timer takes
   * its own configuration changes ({@code configChanges="orientation|screenSize"}) and rebuilds its
   * content view by hand, so a rotation hands over an entirely new stub.
   *
   * <p>⚠️ Whatever was inflated into the <em>old</em> layout is torn down here. It is off the window
   * the moment {@code setContentView} runs, but it is not dead: it holds a WebGL context and its
   * page goes on polling the bridge for the life of the activity. Left in place it also blocks
   * {@link #inflate}, so the cube never comes back and {@link #refresh} drives the orphan — a gap on
   * screen where the cube should be. Nothing is lost by rebuilding: Java holds the whole state and
   * the page is pointed at it again.
   *
   * @param stub the placeholder to inflate the cube into, or null where the layout has none
   */
  public void bind(ViewStub stub) {
    boolean relaidOut = cube != null;
    if (relaidOut) {
      destroy();
    }
    this.stub = stub;
    if (relaidOut && SmartCubeManager.INSTANCE.isConnected()) {
      inflate();
      refresh();
    }
  }

  public void start() {
    SmartCubeManager.INSTANCE.addConnectionListener(this); // replays the connection at once
    SmartCubeManager.INSTANCE.addMoveListener(this);
    SmartCubeManager.INSTANCE.addStateListener(this); // and the current state, which seeds it
    if (cube != null) {
      cube.onResume();
    }
  }

  public void stop() {
    SmartCubeManager.INSTANCE.removeConnectionListener(this);
    SmartCubeManager.INSTANCE.removeMoveListener(this);
    SmartCubeManager.INSTANCE.removeStateListener(this);
    if (cube != null) {
      cube.onPause();
    }
  }

  public void destroy() {
    if (cube != null) {
      cube.destroy();
      cube = null;
    }
    cubeLayout = null;
    veil = null;
    cubeWeb = null;
    stub = null;
  }

  /**
   * Veils the cube: it keeps its place on screen, under a cover that says why it cannot be read.
   *
   * <p>Taking it off screen instead was what this used to do, and it cost the timer its layout —
   * the spacer came back, everything below it moved, and the screen shifted twice per blind
   * attempt. A cover also answers the question the empty space raised, which is why the cube went.
   *
   * <p>The cube under the cover is not drawn while it is up, and is pointed at solved on top of
   * that: a silhouette is both a hint of the state and, one quarter turn in, simply a broken-looking
   * cube, so neither the drawing nor the state it would draw is left to leak one. What the cube
   * really holds is caught up with when the cover comes off.
   *
   * <p>The cover carries no colour of its own. It used to, and a press then repainted the screen's
   * ground everywhere except under it, leaving it outlined as a rectangle that had missed the touch.
   */
  public void setObscured(boolean obscured) {
    if (this.obscured == obscured) {
      return;
    }
    this.obscured = obscured;
    point(); // solved while it is covered, the real state again once it is not
    refresh();
  }

  /**
   * Stands the cube down for a solve, with the rest of the screen's panels.
   *
   * <p>It draws in the gap under the scramble, and a running solve puts the digits and the
   * inspection ring in the middle of the <em>screen</em> — which is that gap. The cube was drawn
   * straight through them. Hidden rather than moved: while a solve runs the screen is the digits and
   * nothing else, and nobody reads a mirror of the cube in their own hands.
   *
   * <p>⚠️ Its space is kept (INVISIBLE, see {@link #refresh}), so standing down cannot shift what is
   * under it mid solve.
   */
  public void setSuppressed(boolean suppressed) {
    if (this.suppressed == suppressed) {
      return;
    }
    this.suppressed = suppressed;
    refresh();
  }

  @Override
  public void onConnection(CubeConnection connection) {
    if (!SmartCubeManager.INSTANCE.isConnected()) {
      // The cube was turned freely while it was away, so what is held here is only the state it
      // was last seen in. Forgetting it is what stops the next connection opening on a confident
      // cube that happens to be a solve out of date.
      seeded = false;
    } else {
      inflate();
      // A cube that connects without turning never reports a state change, so take what it holds.
      seed(SmartCubeManager.INSTANCE.getCurrentState());
    }
    refresh();
  }

  @Override
  public void onMove(CubeMove move) {
    twin.applyMove(move.getFace(), move.isPrime());
    movesSinceState++;
    if (!obscured && cube != null) { // held back rather than dropped: point() catches up after
      cube.addMove(move.getNotation());
    }
  }

  @Override
  public void onState(CubeState state) {
    // The cube's own state is the truth, and the twin is only what has been drawn from the moves:
    // where they differ a move was missed, and pointing the cube at the state again is the whole
    // repair. Re-pointed on a long run of turns too, so the animated alg stays short.
    if (!seeded || movesSinceState >= RESEED_AFTER_MOVES
        || !twin.toFaceCube().equals(state.getFacelets())) {
      seed(state);
      refresh();
    }
  }

  @Override
  public void onCubeDrawn() {
    refresh();
  }

  /** Points both the twin and the cube on screen at a state the physical cube is really in. */
  private void seed(CubeState state) {
    if (state == null || !twin.fromFacelet(state.getFacelets())) {
      return;
    }
    seeded = true;
    point();
  }

  /** Hands the page the state the twin holds, or a solved one while the cover is up. */
  private void point() {
    if (cube == null || !seeded) {
      return;
    }
    movesSinceState = 0;
    cube.setState(CubePatternFormat.format(
        obscured ? CubieCube.SOLVED_FACELET : twin.toFaceCube()));
  }

  private void inflate() {
    if (cube != null || stub == null) {
      return;
    }
    try {
      // ⚠️ Do NOT scale this subtree (ScalingLinearLayout.scaleLateSubtree). A late subtree needs
      // it, but this one is not late: the stub was there for the one scaling pass, its height was
      // scaled then, and inflate() hands that same params object to the view it puts in its place.
      // Scaling again would square the factor — 200px becomes a whole screen on a 1080 phone.
      cubeLayout = stub.inflate();
      stub = null;
      veil = cubeLayout.findViewById(R.id.liveCubeVeil);
      keepUnscaled(veil);
      cubeWeb = cubeLayout.findViewById(R.id.wvLiveCube);
      cube = new VirtualCube((WebView) cubeWeb, touchListener, this);
      cube.setGyroFollowing(true);
      point(); // a state taken before the cube existed still has to reach it
    } catch (Throwable t) {
      // e.g. no WebView implementation installed. Said out loud: swallowed, this is a feature that
      // simply never appears and gives nobody a thread to pull.
      Log.w("LiveCube", "could not inflate the live cube", t);
      // Both, not just the cube: a layout left behind here is never drawn into, but refresh would
      // still reserve its space and hide the spacer — a gap with nothing in it, for good.
      cube = null;
      cubeLayout = null;
      veil = null;
      cubeWeb = null;
    }
  }

  /**
   * Keeps what is drawn over the cube out of the timer layout's scaling pass.
   *
   * <p>That pass runs on the screen's first measure and scales the px the layouts are authored in;
   * the cover is authored in dp and needs none of it. Whether it reached it came down to whether
   * the cube inflated before that measure or after — it inflates during {@code initViews} after a
   * rotation and on the connection event otherwise — so the same thing was drawn at two sizes
   * depending on how the screen had been arrived at. Marking the parent is enough: the pass does
   * not descend into a view it has already done.
   */
  private static void keepUnscaled(View view) {
    if (view != null) {
      view.setTag(R.id.tag_scaled, Boolean.TRUE);
    }
  }

  /**
   * Shown only with a cube connected, only once there is a state to draw, and only while the screen
   * is not standing down for a solve.
   *
   * <p>⚠️ <b>INVISIBLE while the page comes up, never GONE.</b> A GONE WebView is never laid out,
   * so the player would be built into a 0×0 viewport and stay that size once shown — space on
   * screen with nothing in it. INVISIBLE gives the page its real size a beat before it has anything
   * to draw, which costs a moment of reserved space and is the reason the layout jump is small.
   */
  private void refresh() {
    if (cubeLayout == null) {
      return;
    }
    boolean connected = SmartCubeManager.INSTANCE.isConnected();
    // Veiled counts as shown: the cover is what the space is for, and it is over the cube whether
    // or not there is yet a cube under it.
    boolean visible = connected && !suppressed
        && (obscured || (cube != null && cube.isDrawn() && seeded));
    // Faded rather than switched, so standing down for a solve goes with the panels around it.
    // Held in a local: destroy() can null the field before the fade below has ended.
    final View layout = cubeLayout;
    final int hidden = connected ? View.INVISIBLE : View.GONE;
    layout.animate().cancel();
    if (visible) {
      layout.setVisibility(View.VISIBLE);
      layout.animate().alpha(1f).setDuration(FADE_MS).start();
    } else if (layout.getVisibility() != View.VISIBLE) {
      layout.setAlpha(0f); // never shown yet, so there is nothing to fade
      layout.setVisibility(hidden);
    } else {
      layout.animate().alpha(0f).setDuration(FADE_MS)
          .withEndAction(new Runnable() {
            @Override
            public void run() {
              layout.setVisibility(hidden);
            }
          }).start();
    }
    if (veil != null) {
      veil.setVisibility(obscured ? View.VISIBLE : View.GONE);
    }
    if (cubeWeb != null) {
      // INVISIBLE, never GONE: see the warning above, it costs the page its viewport.
      cubeWeb.setVisibility(obscured ? View.INVISIBLE : View.VISIBLE);
    }
  }
}
