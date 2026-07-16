// test-automation: i18n 全局补齐验收
async (page) => {
  const B = 'http://localhost:9876';
  const R = [];
  const sw = async (l) => {
    const sp = page.locator('.header-row1 .lang-switch span');
    await (l==='en' ? sp.last() : sp.first()).click();
    await page.waitForTimeout(300);
  };

  // TC1: EN header name + dark/light + logout
  await page.goto(B+'/dashboard',{waitUntil:'networkidle'}); await page.waitForTimeout(400);
  await sw('en');
  const name = await page.locator('.header-title').textContent().catch(()=>'');
  const right = await page.locator('.header-right').textContent().catch(()=>'');
  R.push({tc:'TC1-EN系统名',pass:name==='Library Management System'});
  R.push({tc:'TC2-EN深色/浅色',pass:right.includes('Light')||right.includes('Dark')});

  // TC3: EN logout
  await page.locator('.header-right .el-dropdown').click();
  await page.waitForTimeout(200);
  const logout = await page.locator('.el-dropdown-menu__item').textContent().catch(()=>'');
  R.push({tc:'TC3-EN退出',pass:logout==='Logout'});
  await page.keyboard.press('Escape');

  // TC4: EN dashboard search
  const st = await page.locator('.search-title').textContent().catch(()=>'');
  const sp = await page.locator('.search-input').getAttribute('placeholder');
  R.push({tc:'TC4-EN仪表盘',pass:st==='Library Collection'&&sp==='Enter your search terms'});

  // TC5: EN book headers
  await page.goto(B+'/book',{waitUntil:'networkidle'}); await page.waitForTimeout(400);
  const hdrs = await page.locator('.el-table__header th').allTextContents();
  const enHdrsOk = hdrs.some(t=>t.includes('ISBN')) && hdrs.some(t=>t.includes('Inventory'));
  R.push({tc:'TC5-EN表格头',pass:enHdrsOk});

  // TC6: EN person page
  await page.goto(B+'/person',{waitUntil:'networkidle'}); await page.waitForTimeout(400);
  const pTitle = await page.locator('h2').first().textContent().catch(()=>'');
  R.push({tc:'TC6-EN个人信息',pass:pTitle==='Profile'});

  // TC7: EN password page
  await page.goto(B+'/password',{waitUntil:'networkidle'}); await page.waitForTimeout(400);
  const pwLabels = await page.locator('.el-form-item__label').allTextContents();
  R.push({tc:'TC7-EN密码页',pass:pwLabels.some(l=>l.includes('Old'))});

  // TC8: ZH restore
  await page.goto(B+'/dashboard',{waitUntil:'networkidle'}); await page.waitForTimeout(400);
  await sw('zh');
  const zhName = await page.locator('.header-title').textContent().catch(()=>'');
  R.push({tc:'TC8-ZH恢复',pass:zhName==='图书馆管理系统'});

  // TC9: No compile error
  const err = await page.locator('iframe').isVisible().catch(()=>true);
  R.push({tc:'TC9-无编译错误',pass:!err});

  const passed = R.filter(r=>r.pass).length;
  return {results:R,passed,total:R.length,summary:`${passed}/${R.length} PASS`};
}
