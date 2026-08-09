/*
  The calmer sticker palette, as a filter over whatever cubing.js drew.

  WHY A FILTER AND NOT A PALETTE.

  The twisty player does not take one. Its scene model exposes background, colorScheme (which is
  the light/dark UI chrome, not the stickers), hintFacelet, faceletScale, a stickering mask and
  two sprite URLs, and nothing else; the six face colours are a private table inside the bundle.
  Read out of it, they are U #FFFFFF, L #FF9900, F #00FF00, R #FF0000, B #2266FF, D #FFFF00 —
  full saturation, which is what makes 54 stickers out-shout everything else on the timer screen.

  WHAT THIS IS FITTED TO.

  Those six, onto the six the mockup used: #E8E6E1 #DE8A3A #4FB35C #D9483F #3C74D4 #E3C33C. Plus
  mid grey onto itself, so a sticker greyed out by a blind stickering mask stays grey.

  WHY THIS SHAPE, gamma -> matrix -> gamma.

  ⚠️ NOTHING HERE HAS A CONSTANT TERM, and that is the whole design. A plain colour matrix fits
  those six almost exactly, but only by carrying an offset — and an offset lifts BLACK, which on a
  cube is the body and every gap between stickers. Measured, the best-fitting affine turned black
  into #292539, a slate purple, and the grid went soft against the ground. With no constant, black
  maps to black at every stage and the grey floor the calm colours need comes out of the closing
  gamma instead, which lifts small values without touching zero.

  The remaining error is a proof, not a shortfall: no single matrix with per-channel curves can hit
  all six, because the blue channel needs orange below red below yellow while a linear map forces
  orange between them whatever sign the green coefficient takes. What is left lands within about
  25/255 on the blue channel of yellow and green, and inside 6 everywhere else.

  Re-derive rather than hand-edit: the constants below came from a least-squares fit over the
  seven pairs above (red weighted double, grey weighted double), sweeping the three gammas.
*/
(function () {
  var MATRIX = "0.71763 0.08271 0.01469 0 0" +
               " 0.10294 0.53516 0.17377 0 0" +
               " 0.02604 0.04986 0.64419 0 0" +
               " 0 0 0 1 0";
  var IN_GAMMA = 1.71;
  var OUT_GAMMA = [0.4902, 0.5435, 0.431];

  var NS = "http://www.w3.org/2000/svg";

  function el(name, attrs) {
    var node = document.createElementNS(NS, name);
    for (var k in attrs) {
      node.setAttribute(k, attrs[k]);
    }
    return node;
  }

  function gammas(exponents) {
    var transfer = el("feComponentTransfer", {});
    ["feFuncR", "feFuncG", "feFuncB"].forEach(function (fn, i) {
      transfer.appendChild(el(fn, { type: "gamma", exponent: exponents[i] }));
    });
    return transfer;
  }

  function install() {
    if (document.getElementById("nt-calm-defs")) {
      return;
    }
    // sRGB, not the SVG default of linearRGB: the fit above is in the space the colours are
    // written in, and letting the filter decode and re-encode round it would undo it.
    var filter = el("filter", { id: "nt-calm", "color-interpolation-filters": "sRGB" });
    filter.appendChild(gammas([IN_GAMMA, IN_GAMMA, IN_GAMMA]));
    filter.appendChild(el("feColorMatrix", { type: "matrix", values: MATRIX }));
    filter.appendChild(gammas(OUT_GAMMA));

    var svg = el("svg", { id: "nt-calm-defs", width: "0", height: "0",
                          "aria-hidden": "true" });
    svg.setAttribute("style", "position:absolute");
    svg.appendChild(filter);
    document.body.appendChild(svg);

    var style = document.createElement("style");
    // Both renderers, so the two puzzles that stay flat are not the only loud ones on the screen.
    style.textContent = "twisty-player,scramble-display{filter:url(#nt-calm)}";
    document.head.appendChild(style);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", install, { once: true });
  } else {
    install();
  }
})();
