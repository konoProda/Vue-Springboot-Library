// test-automation: 登录页布局改造 — UI 验收测试
// 日期: 2026-07-16  目标: Login.vue
async (page) => {
  const BASE = 'http://localhost:9876';
  const results = [];

  await page.goto(BASE + '/login', { waitUntil: 'networkidle' });
  await page.waitForTimeout(800);

  // ==================== TC1: 顶栏 + 系统名 ====================
  const topbar = await page.locator('.login-topbar');
  const tc1a = await topbar.isVisible().catch(() => false);
  const topbarText = tc1a ? (await topbar.textContent()).trim() : '';
  const tc1b = topbarText.includes('图书馆管理系统');
  results.push({ tc: 'TC1-顶栏+系统名', pass: tc1a && tc1b,
    detail: `topbar=${tc1a}, text="${topbarText.substring(0,30)}"` });

  // ==================== TC2: 表单右侧垂直居中 ====================
  const layout = await page.evaluate(() => {
    const body = document.querySelector('.login-body');
    if (!body) return null;
    const s = getComputedStyle(body);
    return { display: s.display, justify: s.justifyContent, align: s.alignItems };
  });
  const tc2 = layout && layout.display === 'flex'
    && layout.justify === 'flex-end'
    && layout.align === 'center';
  results.push({ tc: 'TC2-表单右置居中', pass: tc2,
    detail: layout ? `display=${layout.display} justify=${layout.justify} align=${layout.align}` : 'no .login-body' });

  // ==================== TC3: 背景 cover ====================
  const bg = await page.evaluate(() => {
    const body = document.querySelector('.login-body');
    if (!body) return null;
    const s = getComputedStyle(body);
    return { image: s.backgroundImage.includes('login-bg'), size: s.backgroundSize };
  });
  results.push({ tc: 'TC3-背景cover', pass: bg && bg.image && bg.size === 'cover',
    detail: bg ? `image includes login-bg=${bg.image} size=${bg.size}` : 'no bg' });

  // ==================== TC4: 语言切换 ====================
  // 点击 EN
  const enSpan = page.locator('.topbar-right span').last();
  await enSpan.click();
  await page.waitForTimeout(400);
  const enTitle = await page.locator('.login-page h2').textContent();
  const enPh = await page.locator('input').first().getAttribute('placeholder');
  const tc4a = enTitle === 'System Login' && enPh === 'Username';

  // 点击 简中
  const zhSpan = page.locator('.topbar-right span').first();
  await zhSpan.click();
  await page.waitForTimeout(400);
  const zhTitle = await page.locator('.login-page h2').textContent();
  const zhPh = await page.locator('input').first().getAttribute('placeholder');
  const tc4b = zhTitle === '系统登陆' && zhPh === '用户名';

  results.push({ tc: 'TC4a-EN切换', pass: tc4a, detail: `title="${enTitle}" ph="${enPh}"` });
  results.push({ tc: 'TC4b-ZH切换', pass: tc4b, detail: `title="${zhTitle}" ph="${zhPh}"` });

  // ==================== TC5: 登录功能 ====================
  const captcha = await page.locator('.validate-code').textContent().catch(() => '0000');
  await page.fill('input[placeholder="用户名"]', 'admin');
  await page.fill('input[placeholder="密码"]', '123456');
  await page.fill('input[placeholder="请输入验证码"]', captcha || '0000');
  await page.click('button:has-text("登 录")');
  const tc5 = await page.waitForURL('**/dashboard', { timeout: 8000 }).then(() => true).catch(() => false);
  results.push({ tc: 'TC5-登录功能', pass: tc5, detail: tc5 ? '进入/dashboard' : '登录失败' });

  // ==================== TC6: 无深色模式按钮 ====================
  // Navigate back to login
  await page.goto(BASE + '/login', { waitUntil: 'networkidle' });
  await page.waitForTimeout(500);
  const darkBtn = await page.locator('.topbar-right').textContent();
  const tc6 = !darkBtn.includes('深色') && !darkBtn.includes('浅色') && !darkBtn.includes('☀') && !darkBtn.includes('🌙');
  // Also check: topbar right only has lang switch
  const rightSpans = await page.locator('.topbar-right span').count();
  results.push({ tc: 'TC6-无深色切换', pass: tc6 && rightSpans === 3,
    detail: `right text="${darkBtn.trim()}" spans=${rightSpans}` });

  // ==================== 汇总 ====================
  const passed = results.filter(r => r.pass).length;
  const total = results.length;
  return { results, passed, total, summary: `${passed}/${total} PASS` };
}
