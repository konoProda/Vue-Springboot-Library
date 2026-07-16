// test-automation: overflow 修复验收
async (page) => {
  const B = 'http://localhost:9876';
  const pages = ['/user','/book','/bookwithuser','/lendrecord','/log'];
  const results = [];

  // Resize to small viewport
  await page.setViewportSize({ width: 800, height: 600 });

  for (const p of pages) {
    await page.goto(B + p, { waitUntil: 'networkidle' });
    await page.waitForTimeout(600);

    // Check .table-wrap has overflow:auto
    const overflow = await page.evaluate(() => {
      const tw = document.querySelector('.table-wrap');
      if (!tw) return null;
      const s = getComputedStyle(tw);
      return { overflowX: s.overflowX, overflowY: s.overflowY };
    });

    // Check if table has horizontal scroll visible (content wider than viewport)
    const hasHScroll = await page.evaluate(() => {
      const tw = document.querySelector('.table-wrap');
      return tw ? tw.scrollWidth > tw.clientWidth : false;
    });

    const twExists = !!overflow;
    const overflowAuto = overflow && (overflow.overflowX !== 'hidden');

    results.push({
      page: p,
      twExists,
      overflow: overflow ? `${overflow.overflowX}/${overflow.overflowY}` : 'MISSING',
      hasHScroll,
      pass: twExists && overflowAuto
    });
  }

  // Restore viewport
  await page.setViewportSize({ width: 1280, height: 800 });

  // Check no errors
  const hasErr = await page.locator('iframe').isVisible().catch(() => true);
  results.push({ tc: 'TC-no-errors', pass: !hasErr });

  const passed = results.filter(r => r.pass !== false).length;
  return { results, passed, total: results.length, summary: `${passed}/${results.length} PASS` };
}
