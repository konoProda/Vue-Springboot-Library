// 前端 UI 综合验证 (简化版)
async (page) => {
  const B = 'http://localhost:9876'; const R = [];

  // Login
  await page.goto(B+'/login',{waitUntil:'networkidle'}); await page.waitForTimeout(500);
  const cap = await page.locator('.validate-code').textContent().catch(()=>'0000');
  await page.fill('input[placeholder="用户名"]','admin');
  await page.fill('input[placeholder="密码"]','123456');
  await page.fill('input[placeholder="请输入验证码"]',cap||'0000');
  await page.click('button:has-text("登 录")');
  const logged = await page.waitForURL('**/dashboard',{timeout:8000}).then(()=>true).catch(()=>false);
  R.push({tc:'Login',pass:logged});

  if (!logged) return {results:R,passed:0,total:R.length,summary:'Login failed'};

  // Nav content
  const row2 = await page.locator('.header-row2').textContent().catch(()=>'');
  R.push({tc:'Nav',pass:row2.includes('展示板')&&row2.includes('书籍管理')&&row2.includes('借阅记录')});

  // Dashboard: search + cards + charts
  const hasSearch = await page.locator('.search-title').isVisible().catch(()=>false);
  const cards = await page.locator('.box-card').count();
  const hasChart = await page.locator('#trend-chart').isVisible().catch(()=>false);
  R.push({tc:'Dashboard',pass:hasSearch&&cards===4&&hasChart});

  // 5 pages: sidebar + table wrap
  for (const p of ['/user','/book','/bookwithuser','/lendrecord','/log']) {
    await page.goto(B+p,{waitUntil:'networkidle'}); await page.waitForTimeout(400);
    const ok = await page.locator('.search-sidebar').isVisible().catch(()=>false)
            && await page.locator('.table-wrap').isVisible().catch(()=>false);
    R.push({tc:'Page-'+p.replace('/',''),pass:ok});
  }

  // EN i18n: user page table headers
  const enBtn = await page.locator('.header-row1 span').filter({hasText:'EN'});
  if (await enBtn.isVisible().catch(()=>false)) { await enBtn.click(); await page.waitForTimeout(400); }
  await page.goto(B+'/user',{waitUntil:'networkidle'}); await page.waitForTimeout(400);
  const tbl = await page.locator('.el-table__header').textContent().catch(()=>'');
  R.push({tc:'i18n-EN-user',pass:tbl.includes('Username')||tbl.includes('Reader Management')});

  // Dark mode
  const darkBtn = page.locator('.header-widget').first();
  if (await darkBtn.isVisible().catch(()=>false)) { await darkBtn.click(); await page.waitForTimeout(300); }
  const isDark = await page.evaluate(()=>document.documentElement.classList.contains('dark'));
  R.push({tc:'DarkMode',pass:isDark});

  // Resize: 800px sidebar collapse
  await page.setViewportSize({width:800,height:600}); await page.waitForTimeout(600);
  const sb800 = await page.locator('.search-sidebar').isVisible().catch(()=>false);
  await page.setViewportSize({width:1280,height:800}); await page.waitForTimeout(600);
  const sb1280 = await page.locator('.search-sidebar').isVisible().catch(()=>false);
  R.push({tc:'Resize',pass:!sb800&&sb1280});

  // Action nowrap
  await page.goto(B+'/book',{waitUntil:'networkidle'}); await page.waitForTimeout(400);
  const nw = await page.evaluate(()=>{const a=document.querySelector('.action-btns');return a?getComputedStyle(a).flexWrap==='nowrap':false});
  R.push({tc:'Nowrap',pass:nw});

  // Person → Password button
  await page.goto(B+'/person',{waitUntil:'networkidle'}); await page.waitForTimeout(400);
  const btn = await page.locator('button').filter({hasText:/修改密码|Change Password/}).isVisible().catch(()=>false);
  R.push({tc:'Person→Pwd',pass:btn});

  // All pages no errors
  let allOk=true;
  for (const p of ['/user','/book','/bookwithuser','/lendrecord','/log','/person','/password']) {
    await page.goto(B+p,{waitUntil:'networkidle'}); await page.waitForTimeout(300);
    if (await page.locator('iframe').isVisible().catch(()=>false)) allOk=false;
  }
  R.push({tc:'NoErrors',pass:allOk});

  const passed = R.filter(r=>r.pass).length;
  return {results:R,passed,total:R.length,summary:`${passed}/${R.length} PASS`};
}
