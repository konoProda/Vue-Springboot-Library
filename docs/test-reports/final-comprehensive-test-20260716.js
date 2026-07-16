// test-automation: 全局综合验收测试
async (page) => {
  const B = 'http://localhost:9876'; const R = [];
  const sw = async (l) => {
    const sp = page.locator('.header-row1 .lang-switch span');
    await (l==='en'?sp.last():sp.first()).click(); await page.waitForTimeout(300);
  };

  // ===== 1. Login page i18n =====
  await page.goto(B+'/login',{waitUntil:'networkidle'}); await page.waitForTimeout(400);
  const logo = await page.locator('.topbar-left span').textContent().catch(()=>'');
  R.push({tc:'TC1-login-logo',pass:logo==='Library Management System',detail:logo});

  // ===== 2. Login + success message check =====
  const cap = await page.locator('.validate-code').textContent().catch(()=>'0000');
  await page.fill('input[placeholder="Username"]','admin');
  await page.fill('input[placeholder="Password"]','123456');
  await page.fill('input[placeholder="Enter captcha"]',cap||'0000');
  await page.click('button:has-text("Login")');
  const logged = await page.waitForURL('**/dashboard',{timeout:8000}).then(()=>true).catch(()=>false);
  R.push({tc:'TC2-login-success',pass:logged});

  // ===== 3. EN nav =====
  await page.goto(B+'/dashboard',{waitUntil:'networkidle'}); await page.waitForTimeout(500);
  const nav = await page.locator('.header-row2').textContent().catch(()=>'');
  R.push({tc:'TC3-EN-nav',pass:nav.includes('Dashboard')&&nav.includes('Reader Management')&&nav.includes('Lend Records'),detail:nav.substring(0,60)});

  // ===== 4. EN Dashboard titles =====
  const st = await page.locator('.search-title').textContent().catch(()=>'');
  R.push({tc:'TC4-EN-dashboard',pass:st==='Library Collection',detail:st});

  // ===== 5. EN pagination =====
  await page.goto(B+'/user',{waitUntil:'networkidle'}); await page.waitForTimeout(500);
  const pg = await page.locator('.el-pagination').textContent().catch(()=>'');
  R.push({tc:'TC5-EN-pagination',pass:pg.includes('Total')&&pg.includes('Go to')});

  // ===== 6. EN user table + sex =====
  const sexText = await page.locator('.el-table__body tr').first().locator('td').nth(5).textContent().catch(()=>'');
  R.push({tc:'TC6-EN-sex',pass:sexText==='Male'||sexText==='Female',detail:sexText});

  // ===== 7. Person page: change password button =====
  await page.goto(B+'/person',{waitUntil:'networkidle'}); await page.waitForTimeout(400);
  const cpBtn = await page.locator('text=Change Password').isVisible().catch(()=>false);
  R.push({tc:'TC7-修改密码按钮',pass:cpBtn});

  // ===== 8. Password page: labels in one line =====
  await page.goto(B+'/password',{waitUntil:'networkidle'}); await page.waitForTimeout(400);
  const pLabels = await page.locator('.el-form-item__label').allTextContents();
  R.push({tc:'TC8-EN密码标签',pass:pLabels.some(l=>l==='Confirm New Password')});

  // ===== 9. BW status placeholder =====
  await page.goto(B+'/bookwithuser',{waitUntil:'networkidle'}); await page.waitForTimeout(400);
  const selPh = await page.locator('.search-sidebar .el-select input').getAttribute('placeholder');
  R.push({tc:'TC9-BW-placeholder',pass:selPh==='All',detail:selPh});

  // ===== 10. Log operation types EN =====
  await page.goto(B+'/log',{waitUntil:'networkidle'}); await page.waitForTimeout(400);
  await page.locator('.search-sidebar .el-select').first().click(); await page.waitForTimeout(300);
  const opts = await page.locator('.el-select-dropdown__item').allTextContents();
  const hasBorrow = opts.some(o=>o==='Borrow');
  await page.keyboard.press('Escape');
  R.push({tc:'TC10-Log-opTypes',pass:hasBorrow});

  // ===== 11. No errors =====
  R.push({tc:'TC11-no-errors',pass:!(await page.locator('iframe').isVisible().catch(()=>true))});

  const passed = R.filter(r=>r.pass).length;
  return {results:R,passed,total:R.length,summary:`${passed}/${R.length} PASS`};
}
