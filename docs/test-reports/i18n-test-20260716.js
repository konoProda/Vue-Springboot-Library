// test-automation: i18n 翻译补齐验收
async (page) => {
  const BASE = 'http://localhost:9876';
  const results = [];
  const sw = async (lang) => {
    const sp = page.locator('.header-row1 .lang-switch span');
    if (lang === 'en') await sp.last().click();
    else await sp.first().click();
    await page.waitForTimeout(300);
  };

  // TC1: EN nav
  await page.goto(BASE+'/dashboard',{waitUntil:'networkidle'}); await page.waitForTimeout(400);
  await sw('en');
  const nav = await page.locator('.header-row2').textContent().catch(()=>'');
  results.push({tc:'TC1-EN导航',pass:nav.includes('Dashboard')&&nav.includes('Book Management')});

  // TC2: EN dashboard
  const st = await page.locator('.search-title').textContent().catch(()=>'');
  const sp = await page.locator('.search-input').getAttribute('placeholder');
  const sg = await page.locator('.search-tag').textContent().catch(()=>'');
  results.push({tc:'TC2-EN仪表盘',pass:st==='Library Collection'&&sp==='Enter your search terms'&&sg==='Collection'});

  // TC3: EN book headers
  await page.goto(BASE+'/book',{waitUntil:'networkidle'}); await page.waitForTimeout(400);
  const hdrs = await page.locator('.el-table__header th').allTextContents();
  results.push({tc:'TC3-EN表格头',pass:hdrs.some(t=>t.includes('ISBN'))&&hdrs.some(t=>t.includes('Inventory'))});

  // TC4: ZH restore
  await sw('zh');
  const zhNav = await page.locator('.header-row2').textContent().catch(()=>'');
  results.push({tc:'TC4-ZH恢复',pass:zhNav.includes('展示板')&&zhNav.includes('书籍管理')});

  // TC5: No compile errors
  const err = await page.locator('iframe').isVisible().catch(()=>true);
  results.push({tc:'TC5-无编译错误',pass:!err});

  const passed = results.filter(r=>r.pass).length;
  return {results,passed,total:results.length,summary:`${passed}/${results.length} PASS`};
}
