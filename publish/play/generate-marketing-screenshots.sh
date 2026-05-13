#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$ROOT/publish/play/marketing-screenshots"
TMP="$OUT/.render"
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

mkdir -p "$OUT" "$TMP"

render() {
  local slug="$1"
  local image="$2"
  local headline="$3"
  local subline="$4"
  local accent="$5"
  local html="$TMP/$slug.html"

  cat > "$html" <<HTML
<!doctype html>
<html>
<head>
  <meta charset="utf-8" />
  <style>
    * { box-sizing: border-box; }
    html, body {
      margin: 0;
      width: 1080px;
      height: 1920px;
      overflow: hidden;
      font-family: Inter, Roboto, Arial, sans-serif;
      background:
        radial-gradient(circle at 12% 8%, rgba(212, 241, 166, .95), transparent 280px),
        radial-gradient(circle at 88% 14%, rgba(47, 133, 121, .20), transparent 330px),
        linear-gradient(180deg, #fbfbf1 0%, #eef5e5 100%);
      color: #283127;
    }
    .stage {
      position: relative;
      width: 1080px;
      height: 1920px;
      padding: 88px 76px 70px;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 18px;
      font-size: 30px;
      font-weight: 800;
      letter-spacing: 0;
      color: #20332b;
    }
    .mark {
      width: 56px;
      height: 56px;
      border-radius: 14px;
      display: grid;
      place-items: center;
      color: white;
      font-weight: 900;
      font-size: 34px;
      background: linear-gradient(135deg, #00a88f, #7bd65f);
      box-shadow: 0 12px 26px rgba(25, 97, 79, .25);
    }
    h1 {
      margin: 64px 0 0;
      max-width: 840px;
      font-size: 76px;
      line-height: .98;
      letter-spacing: 0;
      font-weight: 900;
      color: #18231c;
    }
    .subline {
      margin-top: 30px;
      max-width: 820px;
      font-size: 34px;
      line-height: 1.22;
      font-weight: 600;
      color: #566254;
    }
    .phone {
      position: absolute;
      left: 230px;
      bottom: 70px;
      width: 620px;
      height: 1384px;
      border-radius: 66px;
      overflow: hidden;
      background: #f8f8ef;
      border: 12px solid #18231c;
      box-shadow:
        0 34px 80px rgba(28, 49, 38, .24),
        0 8px 20px rgba(28, 49, 38, .13);
    }
    .accent {
      width: 160px;
      height: 16px;
      margin-top: 34px;
      border-radius: 999px;
      background: ${accent};
      box-shadow: 0 12px 30px rgba(62, 94, 69, .13);
    }
    .phone img {
      width: 100%;
      height: 100%;
      display: block;
      object-fit: fill;
    }
    .glow {
      position: absolute;
      right: 96px;
      bottom: 310px;
      width: 260px;
      height: 260px;
      border-radius: 999px;
      background: rgba(188, 231, 115, .42);
      filter: blur(20px);
      z-index: -1;
    }
  </style>
</head>
<body>
  <main class="stage">
    <div class="brand"><div class="mark">S</div><div>SpenIt AICore</div></div>
    <h1>${headline}</h1>
    <div class="subline">${subline}</div>
    <div class="accent"></div>
    <div class="glow"></div>
    <section class="phone">
      <img src="file://${image}" />
    </section>
  </main>
</body>
</html>
HTML

  "$CHROME" \
    --headless=new \
    --disable-gpu \
    --hide-scrollbars \
    --window-size=1080,1920 \
    --screenshot="$OUT/$slug.png" \
    "file://$html" >/dev/null 2>&1
}

render "01-dashboard" "$ROOT/publish/play/captures/01-dashboard.png" "Know what is safe to spend" "A clearer dashboard for daily spending, income context, and recent activity." "#d8f6a9"
render "02-expenses" "$ROOT/publish/play/captures/02-expenses.png" "Search every receipt fast" "Filter expenses by period, category, amount, tags, and tax records." "#dff0d7"
render "03-shared-imports" "$ROOT/publish/play/captures/06-shared-imports.png" "Drop in files to scan" "Import receipts, PDFs, payslips, and bank statements from your phone." "#d5f3c2"
render "04-income" "$ROOT/publish/play/captures/03-income.png" "Track income and net cashflow" "Keep salary, freelance work, refunds, and side income in one place." "#e2efcc"
render "05-insights" "$ROOT/publish/play/captures/04-insights.png" "Let AI explain your spending" "Review trends, summaries, saving tips, and tax-deductible totals." "#c9f2df"
render "06-tax-relief" "$ROOT/publish/play/captures/05-tax-relief.png" "Prepare tax relief records" "Group deductible receipts by year and export supporting files when needed." "#d8f6a9"

sips -g pixelWidth -g pixelHeight "$OUT"/*.png
