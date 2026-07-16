// test-automation: 子页面搜索栏左侧面板改造 — UI 验收测试
// 日期: 2026-07-16  目标: 5个子页面 (User/Book/BookWithUser/LendRecord/Log)
async (page) => {
  const BASE = 'http://localhost:9876';
  const pages = ['/user', '/book', '/bookwithuser', '/lendrecord', '/log'];
  const results = [];

  for (const path of pages) {
    await page.goto(BASE + path, { waitUntil: 'networkidle' });
    await page.waitForTimeout(600);

    // Check sidebar exists
    const sidebar = page.locator('.search-sidebar');
    const hasSidebar = await sidebar.isVisible().catch(() => false);

    // Check sidebar width (22%)
    const sidebarWidth = hasSidebar ? await sidebar.evaluate(el => el.offsetWidth) : 0;
    const vw = await page.evaluate(() => window.innerWidth);
    const ratio = sidebarWidth / vw;
    const tcA = hasSidebar && ratio > 0.15 && ratio < 0.30;

    // Check table-wrap exists
    const tableWrap = page.locator('.table-wrap');
    const hasTableWrap = await tableWrap.isVisible().catch(() => false);

    // Check page-layout exists
    const layout = page.locator('.page-layout');
    const hasLayout = await layout.isVisible().catch(() => false);

    // Check action buttons nowrap (if they exist)
    const actionBtns = page.locator('.action-btns');
    const hasAnyActions = await actionBtns.count() > 0;
    let actionNowrap = true;
    if (hasAnyActions) {
      actionNowrap = await actionBtns.first().evaluate(el => {
        const s = getComputedStyle(el);
        return s.display === 'flex' && s.flexWrap === 'nowrap';
      });
    }

    // Check collapse toggle (should be hidden at desktop width)
    const toggle = page.locator('.sidebar-toggle');
    const toggleVisible = await toggle.isVisible().catch(() => false);

    // Check for compilation errors (iframe with error message)
    const hasErr = await page.locator('iframe').isVisible().catch(() => false) &&
      (await page.locator('iframe').getAttribute('src').catch(() => '')) === '';
    const noCompileError = !hasErr;

    results.push({
      page: path,
      sidebar: hasSidebar,
      sidebarRatio: (ratio * 100).toFixed(1) + '%',
      tableWrap: hasTableWrap,
      layout: hasLayout,
      actionNowrap: hasAnyActions ? actionNowrap : 'N/A',
      toggleDesktop: !toggleVisible,
      noCompileError,
      pass: hasSidebar && hasTableWrap && hasLayout && noCompileError && tcA
    });
  }

  const passed = results.filter(r => r.pass).length;
  return { results, passed, total: results.length, summary: `${passed}/${results.length} PASS` };
}
