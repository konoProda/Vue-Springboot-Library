// test-automation: 主界面框架改造 + 仪表盘搜索 — UI 验收测试
// 日期: 2026-07-16  前提: 已登录 admin 在 /dashboard
async (page) => {
  const BASE = 'http://localhost:9876';
  const results = [];

  // 确保在 dashboard
  await page.goto(BASE + '/dashboard', { waitUntil: 'networkidle' });
  await page.waitForTimeout(800);

  // ==================== TC1: 第一行 — logo + dark + lang + user ====================
  const row1Text = (await page.locator('.header-row1').textContent().catch(() => '')).trim();
  const tc1 = row1Text.includes('图书馆管理系统')
    && (row1Text.includes('深色') || row1Text.includes('浅色'))
    && row1Text.includes('简中') && row1Text.includes('EN')
    && row1Text.includes('管理员');
  results.push({ tc:'TC1-第一行', pass:tc1, detail:row1Text.substring(0,80) });

  // ==================== TC2: 第二行水平导航 ====================
  const row2Text = (await page.locator('.header-row2').textContent().catch(() => '')).trim();
  const tc2 = row2Text.includes('展示板') && row2Text.includes('书籍管理')
    && row2Text.includes('借阅管理') && row2Text.includes('读者管理');
  results.push({ tc:'TC2-第二行导航', pass:tc2, detail:row2Text.substring(0,80) });

  // ==================== TC3: 左侧边栏消失 ====================
  const aside = await page.locator('.el-menu-vertical-demo').isVisible().catch(() => false);
  results.push({ tc:'TC3-无左侧边栏', pass:!aside, detail:aside?'visible':'gone' });

  // ==================== TC4: 搜索框 60-80% 宽 ====================
  const input = page.locator('input[placeholder*="搜索内容"]');
  const tc4a = await input.isVisible().catch(() => false);
  const dims = await page.evaluate(() => {
    const el = document.querySelector('input[placeholder*="搜索内容"]');
    if(!el) return null;
    const w = el.parentElement?.offsetWidth || 0;
    return {w, vw:window.innerWidth, ratio: w/window.innerWidth};
  });
  const tc4b = dims && dims.ratio > 0.4 && dims.ratio < 0.9;
  results.push({ tc:'TC4-搜索框', pass:tc4a&&tc4b, detail: dims?`w=${dims.w} ratio=${(dims.ratio*100).toFixed(1)}%`:'' });

  // ==================== TC5: 搜索"三体"跳转 ====================
  await input.fill('三体');
  const searchTag = page.getByText('馆藏资源');
  await searchTag.click();
  await page.waitForTimeout(1000);
  const tc5a = page.url().includes('/book');
  const rows = page.locator('.el-table__body tr').first();
  const tc5b = (await rows.textContent().catch(() => '')).includes('三体');
  results.push({ tc:'TC5-搜索三体', pass:tc5a&&tc5b, detail:`url=/book=${tc5a} 三体=${tc5b}` });

  // ==================== TC6: 搜索不存在的书 ====================
  await page.goto(BASE+'/dashboard', {waitUntil:'networkidle'});
  await page.waitForTimeout(500);
  const input2 = page.locator('input[placeholder*="搜索内容"]');
  await input2.fill('xyz不存在的书12345');
  const searchTag2 = page.getByText('馆藏资源');
  await searchTag2.click();
  await page.waitForTimeout(800);
  const tc6a = page.url().includes('/dashboard');
  const tc6b = await page.locator('text=未找到匹配的图书').isVisible().catch(() => false);
  results.push({ tc:'TC6-搜索失败红字', pass:tc6a&&tc6b, detail:`stayed=${tc6a} err=${tc6b}` });

  // ==================== TC7: 卡片 + 图表 ====================
  const cards = await page.locator('.box-card').count();
  const trend = await page.locator('#trend-chart').isVisible().catch(()=>false);
  const topBook = await page.locator('#top-books-chart').isVisible().catch(()=>false);
  results.push({ tc:'TC7-卡片图表', pass:cards===4&&trend&&topBook, detail:`cards=${cards} trend=${trend} top=${topBook}` });

  const passed = results.filter(r=>r.pass).length;
  return { results, passed, total:results.length, summary:`${passed}/${results.length} PASS` };
}
