// test-automation: Register.vue sync verification
async (page) => {
  const B = 'http://localhost:9876'; const R = [];
  const sw = async (l) => {
    const sp = page.locator('.login-topbar .topbar-right span');
    await (l==='en'?sp.last():sp.first()).click(); await page.waitForTimeout(300);
  };

  await page.goto(B+'/register',{waitUntil:'networkidle'}); await page.waitForTimeout(400);

  // TC1: EN topbar + form
  const logo = await page.locator('.topbar-left span').textContent().catch(()=>'');
  const title = await page.locator('.login-page h2').textContent().catch(()=>'');
  const ph1 = await page.locator('input').first().getAttribute('placeholder');
  R.push({tc:'TC1-EN表单',pass:logo==='Library Management System'&&title==='User Registration'&&ph1==='Enter username'});

  // TC2: EN role radios
  const radios = await page.locator('.el-radio').allTextContents();
  R.push({tc:'TC2-EN角色',pass:radios.some(t=>t==='Reader')&&radios.some(t=>t==='Admin')});

  // TC3: EN buttons
  const btn = await page.locator('.el-button--primary').textContent().catch(()=>'');
  R.push({tc:'TC3-EN按钮',pass:btn==='Register'});

  // TC4: Form layout (right-aligned)
  const justify = await page.evaluate(() => getComputedStyle(document.querySelector('.login-body')).justifyContent);
  R.push({tc:'TC4-右置居中',pass:justify==='flex-end'});

  // TC5: ZH restore
  await sw('zh');
  const zhTitle = await page.locator('.login-page h2').textContent().catch(()=>'');
  const zhPh1 = await page.locator('input').first().getAttribute('placeholder');
  R.push({tc:'TC5-ZH恢复',pass:zhTitle==='用户注册'&&zhPh1==='请输入用户名'});

  // TC6: No errors
  R.push({tc:'TC6-无错误',pass:!(await page.locator('iframe').isVisible().catch(()=>true))});

  const passed = R.filter(r=>r.pass).length;
  return {results:R,passed,total:R.length,summary:`${passed}/${R.length} PASS`};
}
