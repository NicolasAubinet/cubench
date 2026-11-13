// Builds docs/privacy.html from privacy_policy.md, the one source of the policy:
//     npm run privacy
// The page links only back to the home page. The app links here, and Google Play's payments
// policy forbids reaching a donation link from the app, so nothing on it may lead to GitHub.
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const md = readFileSync(resolve(here, "../../privacy_policy.md"), "utf8").replace(/\r\n/g, "\n");

const esc = s => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
const inline = s => esc(s)
  .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
  .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>')
  .replace(/([\w.+-]+@[\w-]+\.[\w.]+\w)/g, '<a href="mailto:$1">$1</a>');
const slug = s => s.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");

let title = "Privacy Policy";
const body = [];
for (const block of md.split(/\n{2,}/).map(b => b.trim()).filter(Boolean)) {
  const h = block.match(/^(#{1,3})\s+(.*)$/);
  if (h && h[1].length === 1 && body.length === 0) { title = h[2]; continue; }
  if (h) {
    const tag = "h" + Math.min(h[1].length + 1, 4);
    body.push(`<${tag} id="${slug(h[2])}">${inline(h[2])}</${tag}>`);
  } else if (/^[-*] /m.test(block)) {
    body.push("<ul>" + block.split(/\n(?=[-*] )/).map(li => `<li>${inline(li.replace(/^[-*] /, ""))}</li>`).join("") + "</ul>");
  } else {
    body.push(`<p>${inline(block.replace(/\n/g, " "))}</p>`);
  }
}

const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Privacy policy · Cubench</title>
<meta name="description" content="What Cubench keeps on your phone, and the few things it sends: only if you join the coach or send a reconstruction report.">
<link rel="canonical" href="https://cubench.app/privacy.html">
<meta name="theme-color" content="#121518">
<link rel="icon" href="img/icon-192.png" type="image/png">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700;900&display=swap" rel="stylesheet">
<link rel="stylesheet" href="css/site.css">
</head>
<body>
<!-- Generated from privacy_policy.md by tools/website/privacy.mjs. Edit the markdown, not this file. -->
<header class="nav backbar"><div class="wrap">
  <a class="brand" href="./" aria-label="Cubench home"><img src="img/icon-192.png" alt="" width="36" height="36"><span><b>CUBENCH</b><small>SPEEDCUBE TIMER</small></span></a>
</div></header>
<main class="page">
<h1>${inline(title)}</h1>
${body.join("\n")}
</main>
</body>
</html>
`;
writeFileSync(resolve(here, "../../docs/privacy.html"), html);
console.log("Wrote docs/privacy.html");
