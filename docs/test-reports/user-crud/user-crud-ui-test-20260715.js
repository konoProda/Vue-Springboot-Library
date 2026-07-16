// test-automation: 管理员新增和删除用户 — UI 验收测试
// 日期: 2026-07-15  目标: User.vue
async (page) => {
  const BASE = 'http://localhost:9876';
  const TS = Date.now();
  const TEST_USER = 'testreader-' + TS;
  const TEST_PASS = 'test123';
  const results = [];

  async function report(name, passed, detail) {
    results.push({ name, passed, detail });
    console.log(`${passed ? '✅' : '❌'} ${name}: ${detail}`);
  }

  async function login(username, password) {
    await page.goto(BASE + '/login', { waitUntil: 'networkidle' });
    await page.waitForTimeout(500);
    const captchaEl = page.locator('.validate-code');
    let captcha = '0000';
    if (await captchaEl.isVisible().catch(() => false)) {
      captcha = (await captchaEl.textContent()) || '0000';
    }
    await page.fill('input[placeholder="请输入用户名"]', username);
    await page.fill('input[placeholder="请输入密码"]', password);
    await page.fill('input[placeholder="请输入验证码"]', captcha);
    await page.click('button:has-text("登 录")');
    await page.waitForURL('**/dashboard', { timeout: 8000 });
    await page.waitForTimeout(400);
  }

  try {
    // ==================== TC1: 管理员新增读者 (UI) ====================
    console.log('\n=== TC1-UI: 管理员新增读者 ===');
    await login('admin', '123456');

    // 导航到读者管理
    await page.click('text=读者管理');
    await page.waitForTimeout(800);

    // 点击"新增读者"
    await page.click('button:has-text("新增读者")');
    await page.waitForTimeout(500);

    // 验证对话框标题
    const dialogTitle = await page.locator('.el-dialog__title').textContent();
    const hasPassword = await page.locator('text=初始密码').isVisible().catch(() => false);
    await report('TC1a-对话框标题', dialogTitle === '新增读者',
      `标题="${dialogTitle}", 密码字段=${hasPassword}`);

    // 填写表单
    await page.fill('.el-dialog input[placeholder="请输入初始密码"]', TEST_PASS);
    const inputs = page.locator('.el-dialog .el-input__inner');
    const cnt = await inputs.count();
    if (cnt >= 4) {
      await inputs.nth(0).fill(TEST_USER);   // 用户名
      await inputs.nth(2).fill('临时读者-UI'); // 昵称
    }
    await page.waitForTimeout(200);

    // 提交
    await page.click('.el-dialog button:has-text("确 定")');
    await page.waitForTimeout(1000);

    const successMsg = page.locator('.el-message--success');
    const tc1ok = await successMsg.isVisible().catch(() => false);
    await report('TC1b-新增成功', tc1ok, '提示消息可见');

    // ==================== TC2: 新用户登录 (UI) ====================
    console.log('\n=== TC2-UI: 新用户登录 ===');
    await login(TEST_USER, TEST_PASS);
    const currentUrl = page.url();
    const tc2ok = currentUrl.includes('/dashboard');
    await report('TC2-登录成功', tc2ok, `当前URL: ${currentUrl}`);

    // ==================== TC3: 重复用户名 (UI) ====================
    console.log('\n=== TC3-UI: 重复用户名新增失败 ===');
    await login('admin', '123456');
    await page.click('text=读者管理');
    await page.waitForTimeout(800);
    await page.click('button:has-text("新增读者")');
    await page.waitForTimeout(400);

    // 填写相同用户名
    const inputs3 = page.locator('.el-dialog .el-input__inner');
    if (await inputs3.nth(0).isVisible().catch(() => false)) {
      await inputs3.nth(0).fill(TEST_USER);
    }
    await page.fill('.el-dialog input[placeholder="请输入初始密码"]', TEST_PASS);
    await page.waitForTimeout(200);
    await page.click('.el-dialog button:has-text("确 定")');
    await page.waitForTimeout(800);

    const errorMsg3 = page.locator('.el-message--error');
    const tc3ok = await errorMsg3.isVisible().catch(() => false);
    const errText3 = tc3ok ? (await errorMsg3.textContent()) || '' : '';
    await report('TC3-重复拒绝', tc3ok, `错误: "${errText3.trim()}"`);

    // 关闭对话框
    const cancelBtn = page.locator('.el-dialog button:has-text("取 消")');
    if (await cancelBtn.isVisible().catch(() => false)) await cancelBtn.click();
    await page.waitForTimeout(300);

    // ==================== TC4: 有未归还图书时删除失败 (UI) ====================
    console.log('\n=== TC4-UI: 有未归还时删除失败 ===');
    // 先借书给测试用户
    await login(TEST_USER, TEST_PASS);
    await page.goto(BASE + '/book', { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    // 点击借阅
    const borrowBtn = page.getByRole('button', { name: '借阅' }).first();
    await borrowBtn.click();
    await page.waitForTimeout(1000);

    // 管理员删除
    await login('admin', '123456');
    await page.click('text=读者管理');
    await page.waitForTimeout(800);

    // 搜索测试用户
    await page.fill('input[placeholder="请输入姓名"]', '临时读者-UI');
    await page.click('button:has-text("查询")');
    await page.waitForTimeout(500);

    // 点击删除
    const deleteBtn = page.locator('button:has-text("删除")').first();
    if (await deleteBtn.isVisible().catch(() => false)) {
      await deleteBtn.click();
      await page.waitForTimeout(400);
      // 确认弹窗
      const confirmDel = page.locator('.el-popconfirm button:has-text("确定")');
      if (await confirmDel.isVisible().catch(() => false)) {
        await confirmDel.click();
        await page.waitForTimeout(800);
      }
    }

    const errorMsg4 = page.locator('.el-message--error');
    const tc4ok = await errorMsg4.isVisible().catch(() => false);
    const errText4 = tc4ok ? (await errorMsg4.textContent()) || '' : '';
    await report('TC4-删除被拒', tc4ok, `错误: "${errText4.trim()}"`);

    // ==================== TC5: 归还后删除成功 (UI) ====================
    console.log('\n=== TC5-UI: 归还后删除成功 ===');
    // 还书
    await login(TEST_USER, TEST_PASS);
    await page.goto(BASE + '/book', { waitUntil: 'networkidle' });
    await page.waitForTimeout(800);
    const returnBtn = page.locator('button:has-text("还书")').first();
    if (await returnBtn.isVisible().catch(() => false)) {
      await returnBtn.click();
      await page.waitForTimeout(400);
      const confirmRet = page.locator('.el-popconfirm button:has-text("确认")');
      if (await confirmRet.isVisible().catch(() => false)) {
        await confirmRet.click();
        await page.waitForTimeout(1000);
      }
    }

    // 管理员删除
    await login('admin', '123456');
    await page.click('text=读者管理');
    await page.waitForTimeout(800);
    await page.fill('input[placeholder="请输入姓名"]', '临时读者-UI');
    await page.click('button:has-text("查询")');
    await page.waitForTimeout(500);

    const deleteBtn5 = page.locator('button:has-text("删除")').first();
    if (await deleteBtn5.isVisible().catch(() => false)) {
      await deleteBtn5.click();
      await page.waitForTimeout(400);
      const confirmDel5 = page.locator('.el-popconfirm button:has-text("确定")');
      if (await confirmDel5.isVisible().catch(() => false)) {
        await confirmDel5.click();
        await page.waitForTimeout(800);
      }
    }

    const successMsg5 = page.locator('.el-message--success');
    const tc5ok = await successMsg5.isVisible().catch(() => false);
    await report('TC5-删除成功', tc5ok, '提示消息可见');

    // ==================== TC6: 被删除用户无法登录 ====================
    console.log('\n=== TC6-UI: 被删除用户无法登录 ===');
    await login(TEST_USER, TEST_PASS);
    const currentUrl6 = page.url();
    const tc6ok = currentUrl6.includes('/login');
    await report('TC6-登录失败', tc6ok, `当前URL: ${currentUrl6}`);

  } catch (e) {
    console.error('TEST ERROR:', e.message);
  }

  console.log('\n========== UI 测试汇总 ==========');
  const passed = results.filter(r => r.passed).length;
  const total = results.length;
  results.forEach(r => console.log(`${r.passed ? '✅' : '❌'} ${r.name}`));
  console.log(`\n通过: ${passed}/${total}`);
  return { results, passed, total };
}
