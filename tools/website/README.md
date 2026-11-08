# Website tools

The site in `docs/` is plain HTML, CSS and JS, served by GitHub Pages from `master` + `/docs`. Edit it directly; nothing needs building. Two files are generated here:

- `npm run privacy` writes `docs/privacy.html` from `privacy_policy.md`, the one source of the policy. Run it after every change to the policy. The page links only back to the home page: the app links to it, and nothing reachable from the app may lead to a donation link (Google Play payments policy).
- `npm run build` writes `docs/vendor/twisty.js` (cubing.js 3D player for the home page hero, one self-hosted file) and copies the app's calm sticker palette to `docs/js/palette.js`. Run it after bumping cubing.js or changing the palette.

Run `npm install` once first. The hero's solve data is `docs/js/hero-solve.js`.
