// Test: 图书库存数量验收测试 - 2026-07-15
// Run via: browser_run_code_unsafe
async (page) => {
  const BASE = 'http://localhost:9876';
  const TEST_ISBN = 'TEST-INV-001';
  const TEST_NAME = '测试库存图书';
  const results = [];

  // ========== Helpers ==========
  async function report(name, passed, detail) {
    results.push({ name, passed, detail });
    console.log(`${passed ? '✅' : '❌'} ${name}: ${detail}`);
  }

  async function login(username, password) {
    await page.goto(BASE + '/login', { waitUntil: 'networkidle' });
    await page.waitForSelector('input[placeholder="请输入用户名"]', { timeout: 5000 });
    await page.fill('input[placeholder="请输入用户名"]', username);
    await page.fill('input[placeholder="请输入密码"]', password);
    const captchaEl = page.locator('.validate-code');
    if (await captchaEl.isVisible()) {
      const captcha = await captchaEl.textContent();
      await page.fill('input[placeholder="请输入验证码"]', captcha || '0000');
    }
    await page.click('button:has-text("登录")');
    await page.waitForURL('**/dashboard', { timeout: 8000 });
    await page.waitForTimeout(500);
  }

  async function navigateToBooks() {
    // Try admin "图书管理" or reader "图书查询"
    const adminLink = page.locator('text=图书管理');
    const readerLink = page.locator('text=图书查询');
    if (await adminLink.isVisible()) await adminLink.click();
    else if (await readerLink.isVisible()) await readerLink.click();
    await page.waitForTimeout(800);
  }

  async function searchBook(name) {
    const searchInput = page.locator('input[placeholder="请输入图书名称"]');
    if (await searchInput.isVisible()) {
      await searchInput.fill(name);
      await page.click('button:has-text("查询")');
      await page.waitForTimeout(800);
    }
  }

  async function closeDialog() {
    const cancelBtn = page.locator('.el-dialog button:has-text("取 消")');
    if (await cancelBtn.isVisible()) await cancelBtn.click();
    await page.waitForTimeout(300);
  }

  try {
    // =====================================================
    // TC1: 新增 totalCopies=1 的图书 → 页面显示可借1/馆藏1
    // =====================================================
    console.log('\n=== TC1: 新增图书 ===');
    await login('admin', '123456');
    await navigateToBooks();

    // Click "上架"
    await page.click('button:has-text("上架")');
    await page.waitForTimeout(500);

    // Fill form
    const dialogInputs = page.locator('.el-dialog .el-input__inner');
    const count = await dialogInputs.count();
    if (count >= 5) {
      await dialogInputs.nth(0).fill(TEST_ISBN);
      await dialogInputs.nth(1).fill(TEST_NAME);
      await dialogInputs.nth(2).fill('29.90');
      await dialogInputs.nth(3).fill('测试作者');
      await dialogInputs.nth(4).fill('测试出版社');
    }
    // Set totalCopies
    const numInput = page.locator('.el-dialog .el-input-number input').first();
    await numInput.fill('1');
    await page.waitForTimeout(300);

    // Submit
    await page.click('.el-dialog button:has-text("确 定")');
    await page.waitForTimeout(1500);

    // Search and verify
    await searchBook(TEST_NAME);
    const row1 = page.locator('.el-table__body tr', { hasText: TEST_ISBN }).first();
    const cell1 = row1.locator('td').nth(6);
    const text1 = (await cell1.textContent()) || '';
    const tc1ok = text1.includes('可借') && text1.includes('1') && text1.includes('馆藏');
    await report('TC1', tc1ok, `库存单元格内容: "${text1.trim()}"`);

    // =====================================================
    // TC2: 读者A借阅该书 → 可借变为0
    // =====================================================
    console.log('\n=== TC2: 读者A借阅 ===');
    await login('reader', '123456');
    await navigateToBooks();
    await searchBook(TEST_NAME);

    // Click borrow
    const borrowBtn = page.locator('button:has-text("借阅")').first();
    await borrowBtn.click();
    await page.waitForTimeout(1500);

    // Check success message
    const successMsg = page.locator('.el-message--success');
    const hasSuccess = await successMsg.isVisible().catch(() => false);

    // Verify availableCopies = 0
    await page.waitForTimeout(800);
    await searchBook(TEST_NAME);
    const row2 = page.locator('.el-table__body tr', { hasText: TEST_ISBN }).first();
    const cell2 = row2.locator('td').nth(6);
    const text2 = (await cell2.textContent()) || '';
    const tc2ok = text2.includes('已借完');
    await report('TC2', tc2ok, `借阅后库存: "${text2.trim()}", 借阅消息: ${hasSuccess}`);

    // =====================================================
    // TC3: 读者B点击已借完的书 → disabled + 库存不足提示
    // =====================================================
    console.log('\n=== TC3: 读者B借阅 ===');
    await login('reader2', '123456');
    await navigateToBooks();
    await searchBook(TEST_NAME);

    const borrowBtn3 = page.locator('button:has-text("借阅")').first();
    const isDisabled = await borrowBtn3.isDisabled();
    await borrowBtn3.click();
    await page.waitForTimeout(500);
    const errorMsg3 = page.locator('.el-message--error');
    const hasError3 = await errorMsg3.isVisible().catch(() => false);
    const errText3 = hasError3 ? (await errorMsg3.textContent()) || '' : '';
    const tc3ok = isDisabled && errText3.includes('库存不足');
    await report('TC3', tc3ok, `disabled=${isDisabled}, 错误消息: "${errText3.trim()}"`);

    // =====================================================
    // TC4: 读者A还书 → 可借恢复为1
    // =====================================================
    console.log('\n=== TC4: 读者A还书 ===');
    await login('reader', '123456');
    await navigateToBooks();
    await searchBook(TEST_NAME);

    // Click return button
    const returnBtn = page.locator('button:has-text("还书")').first();
    await returnBtn.click();
    await page.waitForTimeout(500);
    // Click confirm in popconfirm
    const confirmBtn = page.locator('.el-popconfirm button:has-text("确认")');
    if (await confirmBtn.isVisible()) await confirmBtn.click();
    await page.waitForTimeout(1500);

    // Verify availableCopies = 1
    await searchBook(TEST_NAME);
    const row4 = page.locator('.el-table__body tr', { hasText: TEST_ISBN }).first();
    const cell4 = row4.locator('td').nth(6);
    const text4 = (await cell4.textContent()) || '';
    const tc4ok = text4.includes('可借 1');
    await report('TC4', tc4ok, `还书后库存: "${text4.trim()}"`);

    // =====================================================
    // TC5: 管理员改 totalCopies=0(有1本借出) → 拒绝
    // =====================================================
    console.log('\n=== TC5: 管理员改馆藏约束 ===');
    // First, reader borrows again
    await login('reader', '123456');
    await navigateToBooks();
    await searchBook(TEST_NAME);
    const borrowBtn5 = page.locator('button:has-text("借阅")').first();
    await borrowBtn5.click();
    await page.waitForTimeout(1000);

    // Login as admin
    await login('admin', '123456');
    await navigateToBooks();
    await searchBook(TEST_NAME);

    // Click edit
    const editBtn = page.locator('button:has-text("编辑")').first();
    await editBtn.click();
    await page.waitForTimeout(500);

    // Set totalCopies to 0
    const numInput5 = page.locator('.el-dialog .el-input-number input').first();
    await numInput5.fill('0');
    await page.waitForTimeout(200);

    // Submit
    await page.click('.el-dialog button:has-text("确 定")');
    await page.waitForTimeout(800);

    const errorMsg5 = page.locator('.el-message--error');
    const hasError5 = await errorMsg5.isVisible().catch(() => false);
    const errText5 = hasError5 ? (await errorMsg5.textContent()) || '' : '';
    const tc5ok = errText5.includes('馆藏总数') || errText5.includes('已借出');
    await report('TC5', tc5ok, `错误消息: "${errText5.trim()}"`);

    await closeDialog();

    // =====================================================
    // TC6: 刷新页面后数据持久化
    // =====================================================
    console.log('\n=== TC6: 刷新持久化 ===');
    await page.reload();
    await page.waitForTimeout(1000);
    await navigateToBooks();
    await searchBook(TEST_NAME);

    const row6 = page.locator('.el-table__body tr', { hasText: TEST_ISBN }).first();
    const cell6 = row6.locator('td').nth(6);
    const text6 = (await cell6.textContent()) || '';
    const tc6ok = text6.includes('馆藏 1') && text6.includes('已借完');
    await report('TC6', tc6ok, `刷新后库存: "${text6.trim()}"`);

    // =====================================================
    // TC7: 读者点击已借过的书 → disabled + 不可重复提示
    // =====================================================
    console.log('\n=== TC7: 重复借阅提示 ===');
    await login('reader', '123456');
    await navigateToBooks();
    await searchBook(TEST_NAME);

    const borrowBtn7 = page.locator('button:has-text("借阅")').first();
    const isDisabled7 = await borrowBtn7.isDisabled();
    await borrowBtn7.click();
    await page.waitForTimeout(500);
    const errorMsg7 = page.locator('.el-message--error');
    const hasError7 = await errorMsg7.isVisible().catch(() => false);
    const errText7 = hasError7 ? (await errorMsg7.textContent()) || '' : '';
    const tc7ok = isDisabled7 && errText7.includes('不可重复借阅');
    await report('TC7', tc7ok, `disabled=${isDisabled7}, 错误消息: "${errText7.trim()}"`);

  } catch (e) {
    console.error('TEST ERROR:', e.message);
  }

  // ========== Summary ==========
  console.log('\n========== 测试报告汇总 ==========');
  const passed = results.filter(r => r.passed).length;
  const total = results.length;
  results.forEach(r => console.log(`${r.passed ? '✅' : '❌'} ${r.name}`));
  console.log(`\n通过: ${passed}/${total}`);
  return { results, passed, total };
}
