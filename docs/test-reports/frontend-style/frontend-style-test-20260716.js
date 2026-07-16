// test-automation: 前端界面风格改造 — UI 验收测试
// 日期: 2026-07-16  目标: global.css / Header.vue / Login.vue
async (page) => {
  const BASE = 'http://localhost:9876';
  const results = [];

  // ==================== TC1: 全局字体 ====================
  console.log('\n=== TC1: 全局字体检查 ===');
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' });
  await page.waitForTimeout(800);

  const ff = await page.evaluate(() =>
    getComputedStyle(document.body).fontFamily
  );
  const tc1ok = ff.includes('SimHei') || ff.includes('Microsoft YaHei');
  results.push({ name: 'TC1-全局字体', pass: tc1ok, detail: ff.substring(0, 80) });
  console.log(`${tc1ok ? '✅' : '❌'} TC1: ${ff}`);

  // ==================== TC2: 登录页语言切换按钮 ====================
  console.log('\n=== TC2: 语言切换按钮 ===');
  const langSwitch = page.locator('.lang-switch');
  const tc2ok = await langSwitch.isVisible().catch(() => false);
  const langText = tc2ok ? (await langSwitch.textContent()).trim() : 'NOT FOUND';
  results.push({ name: 'TC2-语言切换按钮', pass: tc2ok, detail: langText });
  console.log(`${tc2ok ? '✅' : '❌'} TC2: "${langText}"`);

  // ==================== TC3: 切换到英文 ====================
  console.log('\n=== TC3: 切换到英文 ===');
  // Click "EN"
  const enSpan = page.locator('.lang-switch span', { hasText: 'EN' });
  await enSpan.click();
  await page.waitForTimeout(500);

  // Check placeholder
  const userInput = page.locator('input').first();
  const userPlaceholder = await userInput.getAttribute('placeholder');
  const title = await page.locator('.login-page h2').textContent();
  const loginBtn = await page.locator('.login-page button').first().textContent();

  const tc3ok = userPlaceholder === 'Username'
    && title === 'System Login'
    && loginBtn.trim() === 'Login';
  results.push({ name: 'TC3-英文切换', pass: tc3ok,
    detail: `placeholder="${userPlaceholder}" title="${title}" btn="${loginBtn.trim()}"` });
  console.log(`${tc3ok ? '✅' : '❌'} TC3: placeholder=${userPlaceholder} title=${title} btn=${loginBtn.trim()}`);

  // ==================== TC4: 切回中文 ====================
  console.log('\n=== TC4: 切回中文 ===');
  const zhSpan = page.locator('.lang-switch span', { hasText: '简中' });
  await zhSpan.click();
  await page.waitForTimeout(500);

  const userPlaceholder2 = await page.locator('input').first().getAttribute('placeholder');
  const title2 = await page.locator('.login-page h2').textContent();
  const loginBtn2 = await page.locator('.login-page button').first().textContent();

  const tc4ok = userPlaceholder2 === '用户名'
    && title2 === '系统登陆'
    && loginBtn2.trim() === '登 录';
  results.push({ name: 'TC4-切回中文', pass: tc4ok,
    detail: `placeholder="${userPlaceholder2}" title="${title2}" btn="${loginBtn2.trim()}"` });
  console.log(`${tc4ok ? '✅' : '❌'} TC4: placeholder=${userPlaceholder2} title=${title2} btn=${loginBtn2.trim()}`);

  // ==================== TC5: 刷新后语言保持 ====================
  console.log('\n=== TC5: 刷新后语言保持 ===');
  // Switch to EN first
  await page.locator('.lang-switch span', { hasText: 'EN' }).click();
  await page.waitForTimeout(300);
  // Reload
  await page.reload({ waitUntil: 'networkidle' });
  await page.waitForTimeout(800);

  const userPlaceholder3 = await page.locator('input').first().getAttribute('placeholder');
  const tc5ok = userPlaceholder3 === 'Username';
  results.push({ name: 'TC5-刷新保持', pass: tc5ok, detail: `placeholder="${userPlaceholder3}"` });
  console.log(`${tc5ok ? '✅' : '❌'} TC5: ${userPlaceholder3}`);

  // ==================== TC6: 顶部栏加粗 ====================
  console.log('\n=== TC6: 顶部栏 font-weight ===');
  // Login as admin to see header
  const captcha = await page.locator('.validate-code').textContent().catch(() => '0000');
  await page.locator('.lang-switch span', { hasText: '简中' }).click();
  await page.waitForTimeout(200);
  await page.fill('input[placeholder="用户名"]', 'admin');
  await page.fill('input[placeholder="密码"]', '123456');
  await page.fill('input[placeholder="请输入验证码"]', captcha || '0000');
  await page.click('button:has-text("登 录")');
  await page.waitForURL('**/dashboard', { timeout: 8000 });
  await page.waitForTimeout(500);

  const headerFw = await page.evaluate(() => {
    const header = document.querySelector('.header-bar');
    return header ? getComputedStyle(header).fontWeight : 'NO HEADER';
  });
  const tc6ok = headerFw === '600' || headerFw === 'bold' || headerFw === '700';
  results.push({ name: 'TC6-顶栏加粗', pass: tc6ok, detail: `font-weight=${headerFw}` });
  console.log(`${tc6ok ? '✅' : '❌'} TC6: font-weight=${headerFw}`);

  // ==================== 汇总 ====================
  console.log('\n========== UI 测试汇总 ==========');
  const passed = results.filter(r => r.pass).length;
  const total = results.length;
  results.forEach(r => console.log(`${r.pass ? '✅' : '❌'} ${r.name}: ${r.detail}`));
  console.log(`\n通过: ${passed}/${total}`);
  return { results, passed, total };
}
