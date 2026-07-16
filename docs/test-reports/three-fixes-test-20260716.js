// test-automation: 三处修复 — UI 验收测试
async (page) => {
  const BASE = 'http://localhost:9876';
  const results = [];

  // Fix1: Log 时间拆分
  await page.goto(BASE + '/log', { waitUntil: 'networkidle' });
  await page.waitForTimeout(500);
  const hasStart = await page.locator('input[placeholder="开始时间"]').isVisible().catch(() => false);
  const hasEnd = await page.locator('input[placeholder="结束时间"]').isVisible().catch(() => false);
  results.push({ tc: 'Fix1-Log时间拆分', pass: hasStart && hasEnd, detail: `start=${hasStart} end=${hasEnd}` });

  // Fix2: Header "借阅记录"
  await page.goto(BASE + '/dashboard', { waitUntil: 'networkidle' });
  await page.waitForTimeout(400);
  const navText = await page.locator('.header-row2').textContent().catch(() => '');
  results.push({ tc: 'Fix2-借阅记录改名', pass: navText.includes('借阅记录') && !navText.includes('借阅管理') });

  // Fix3a: BookWithUser 筛选选项
  await page.goto(BASE + '/bookwithuser', { waitUntil: 'networkidle' });
  await page.waitForTimeout(400);
  await page.locator('.search-sidebar .el-select').first().click();
  await page.waitForTimeout(300);
  const bwOpts = await page.locator('.el-select-dropdown__item').allTextContents();
  const bwOk = bwOpts.some(t => t.includes('未逾期'));
  await page.keyboard.press('Escape'); await page.waitForTimeout(200);
  results.push({ tc: 'Fix3a-BW未逾期选项', pass: bwOk, detail: bwOpts.join(',') });

  // Fix3b: LendRecord 筛选选项
  await page.goto(BASE + '/lendrecord', { waitUntil: 'networkidle' });
  await page.waitForTimeout(400);
  await page.locator('.search-sidebar .el-select').first().click();
  await page.waitForTimeout(300);
  const lrOpts = await page.locator('.el-select-dropdown__item').allTextContents();
  const lrOk = lrOpts.some(t => t.includes('已归还')) && lrOpts.some(t => t.includes('未归还'));
  await page.keyboard.press('Escape');
  results.push({ tc: 'Fix3b-LR已归还+未归还选项', pass: lrOk, detail: lrOpts.join(',') });

  const passed = results.filter(r => r.pass).length;
  return { results, passed, total: results.length, summary: `${passed}/${results.length} PASS` };
}
