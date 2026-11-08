// Builds docs/vendor/twisty.js for the website hero, and copies the app's calm sticker palette
// so the site's cube matches the app's. Run after `npm install` when bumping cubing.js:
//     npm run build
import { build } from "esbuild";
import { copyFileSync, mkdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const docs = resolve(here, "../../docs");
mkdirSync(resolve(docs, "vendor"), { recursive: true });

await build({
  entryPoints: [resolve(here, "entry.mjs")],
  bundle: true,
  splitting: false, // one file: GitHub Pages serves it as is
  format: "esm",
  minify: true,
  target: ["es2020"],
  legalComments: "linked", // three.js and cubing.js notices ship beside the bundle
  outfile: resolve(docs, "vendor/twisty.js"),
});

copyFileSync(resolve(here, "../../nanotimer/src/main/assets/scramble/palette.js"), resolve(docs, "js/palette.js"));
console.log("Wrote docs/vendor/twisty.js and docs/js/palette.js");
