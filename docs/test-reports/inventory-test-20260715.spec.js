// @ts-check
const { test, expect } = require('@playwright/test');

const BASE = 'http://localhost:9876';
const TEST_BOOK_ISBN = 'TEST-INV-001';
const TEST_BOOK_NAME = '测试库存图书';

// ========== Helpers ==========

async function login(page, username, password) {
  await page.goto(BASE + '/login');
  await page.waitForSelector('input[placeholder="请输入用户名"]');
  await page.fill('input[placeholder="请输入用户名"]', username);
  await page.fill('input[placeholder="请输入密码"]', password);
  // fill captcha
  const captchaText = await page.$eval('.validate-code', el => el.textContent || '');
  await page.fill('input[placeholder="请输入验证码"]', captchaText || '0000');
  await page.click('button:has-text("登录")');
  await page.waitForURL('**/dashboard', { timeout: 5000 });
}

async function logout(page) {
  await page.click('.header-right .el-dropdown'); // click dropdown
  await page.waitForTimeout(300);
  await page.click('text=退出登录');
  await page.waitForURL('**/login', { timeout: 5000 });
}

async function navigateToBooks(page) {
  await page.click('text=图书管理');
  await page.waitForSelector('.el-table', { timeout: 5000 });
}

async function navigateToBookSearch(page) {
  await page.click('text=图书查询');
  await page.waitForSelector('.el-table', { timeout: 5000 });
}

// ========== Clean up before all ==========
test.describe('图书库存数量 - 验收测试', () => {

  test.beforeAll(async () => {
    // Direct cleanup via page context
  });

  test('TC1: 新增 totalCopies=1 的图书 → 页面显示可借1/馆藏1', async ({ page }) => {
    // 1. Admin login
    await login(page, 'admin', '123456');
    await navigateToBooks(page);

    // 2. Click "上架" button
    await page.click('button:has-text("上架")');
    await page.waitForSelector('.el-dialog', { timeout: 3000 });

    // 3. Fill book form
    await page.fill('.el-dialog input[placeholder="请输入图书编号"]', TEST_BOOK_ISBN);
    await page.waitForTimeout(200);
    // The dialog has multiple inputs — fill by placeholder
    const inputs = page.locator('.el-dialog .el-input__inner');
    await inputs.nth(0).fill(TEST_BOOK_ISBN);  // isbn
    await inputs.nth(1).fill(TEST_BOOK_NAME);   // name
    await inputs.nth(2).fill('29.90');          // price
    await inputs.nth(3).fill('测试作者');        // author
    await inputs.nth(4).fill('测试出版社');      // publisher

    // Set totalCopies = 1 (el-input-number)
    const numberInput = page.locator('.el-dialog .el-input-number input');
    await numberInput.fill('1');

    // 4. Submit
    await page.click('.el-dialog button:has-text("确 定")');
    await page.waitForTimeout(1000);

    // 5. Search for the new book
    await page.fill('input[placeholder="请输入图书名称"]', TEST_BOOK_NAME);
    await page.click('button:has-text("查询")');
    await page.waitForTimeout(500);

    // 6. Verify the row shows correct inventory
    const row = page.locator('.el-table__body tr', { hasText: TEST_BOOK_ISBN });
    const inventoryCell = row.locator('td').nth(6); // "库存" column
    const text = await inventoryCell.textContent();
    expect(text).toContain('可借 1');
    expect(text).toContain('馆藏 1');
  });

  test('TC2: 读者A借阅该书 → 可借变为0', async ({ page }) => {
    // 1. Reader login
    await login(page, 'reader', '123456');
    await navigateToBookSearch(page);

    // 2. Search the test book
    await page.fill('input[placeholder="请输入图书名称"]', TEST_BOOK_NAME);
    await page.click('button:has-text("查询")');
    await page.waitForTimeout(500);

    // 3. Click borrow
    const borrowBtn = page.locator('button:has-text("借阅")').first();
    await borrowBtn.click();
    await page.waitForTimeout(1500);

    // 4. Verify toast message
    const msg = page.locator('.el-message--success');
    await expect(msg).toBeVisible({ timeout: 3000 });

    // 5. Reload and verify availableCopies = 0
    await page.waitForTimeout(500);
    await page.click('button:has-text("查询")');
    await page.waitForTimeout(500);

    const row = page.locator('.el-table__body tr', { hasText: TEST_BOOK_ISBN });
    const inventoryCell = row.locator('td').nth(6);
    const text = await inventoryCell.textContent();
    expect(text).toContain('已借完');
    expect(text).toContain('馆藏 1');
  });

  test('TC3: 读者B点击已借完的书 → disabled + 库存不足提示', async ({ page }) => {
    // 1. ReaderB login
    await login(page, 'reader2', '123456');
    await navigateToBookSearch(page);

    // 2. Search the test book
    await page.fill('input[placeholder="请输入图书名称"]', TEST_BOOK_NAME);
    await page.click('button:has-text("查询")');
    await page.waitForTimeout(500);

    // 3. Verify borrow button is disabled
    const borrowBtn = page.locator('button:has-text("借阅")').first();
    await expect(borrowBtn).toBeDisabled({ timeout: 3000 });

    // 4. Click anyway — should show error message
    await borrowBtn.click();
    const errorMsg = page.locator('.el-message--error');
    await expect(errorMsg).toBeVisible({ timeout: 3000 });
    const msgText = await errorMsg.textContent();
    expect(msgText).toContain('库存不足');
  });

  test('TC4: 读者A还书 → 可借恢复为1', async ({ page }) => {
    // 1. Reader login
    await login(page, 'reader', '123456');
    await navigateToBookSearch(page);

    // 2. Search the test book
    await page.fill('input[placeholder="请输入图书名称"]', TEST_BOOK_NAME);
    await page.click('button:has-text("查询")');
    await page.waitForTimeout(500);

    // 3. Click return button (danger style)
    const returnBtn = page.locator('button:has-text("还书")').first();
    await returnBtn.click();
    // Wait for popconfirm to appear
    await page.waitForTimeout(500);
    await page.click('.el-popconfirm button:has-text("确认")');
    await page.waitForTimeout(1500);

    // 4. Verify success message
    const msg = page.locator('.el-message--success');
    await expect(msg).toBeVisible({ timeout: 3000 });

    // 5. Verify availableCopies = 1
    await page.click('button:has-text("查询")');
    await page.waitForTimeout(500);

    const row = page.locator('.el-table__body tr', { hasText: TEST_BOOK_ISBN });
    const inventoryCell = row.locator('td').nth(6);
    const text = await inventoryCell.textContent();
    expect(text).toContain('可借 1');
    expect(text).toContain('馆藏 1');
  });

  test('TC5: 管理员将已借出1本的书 totalCopies 改为0 → 拒绝', async ({ page }) => {
    // 1. First, reader borrows the book
    await login(page, 'reader', '123456');
    await navigateToBookSearch(page);
    await page.fill('input[placeholder="请输入图书名称"]', TEST_BOOK_NAME);
    await page.click('button:has-text("查询")');
    await page.waitForTimeout(500);
    const borrowBtn = page.locator('button:has-text("借阅")').first();
    await borrowBtn.click();
    await page.waitForTimeout(1000);

    // 2. Login as admin
    await login(page, 'admin', '123456');
    await navigateToBooks(page);

    // 3. Search the test book
    await page.fill('input[placeholder="请输入图书名称"]', TEST_BOOK_NAME);
    await page.click('button:has-text("查询")');
    await page.waitForTimeout(500);

    // 4. Click edit
    const editBtn = page.locator('button:has-text("编辑")').first();
    await editBtn.click();
    await page.waitForSelector('.el-dialog', { timeout: 3000 });

    // 5. Change totalCopies to 0
    const numberInput = page.locator('.el-dialog .el-input-number input');
    await numberInput.fill('0');
    await page.waitForTimeout(200);

    // 6. Submit — should be rejected
    await page.click('.el-dialog button:has-text("确 定")');
    await page.waitForTimeout(500);

    // 7. Verify error message (either frontend or backend)
    const errorMsg = page.locator('.el-message--error');
    await expect(errorMsg).toBeVisible({ timeout: 3000 });
    const msgText = await errorMsg.textContent();
    expect(msgText).toContain('馆藏总数');
  });

  test('TC6: 刷新页面后数据持久化正确', async ({ page }) => {
    // 1. Reader login and navigate
    await login(page, 'reader', '123456');
    await navigateToBookSearch(page);

    // 2. Search the test book
    await page.fill('input[placeholder="请输入图书名称"]', TEST_BOOK_NAME);
    await page.click('button:has-text("查询")');
    await page.waitForTimeout(500);

    // 3. Refresh page
    await page.reload();
    await page.waitForTimeout(1000);

    // 4. Navigate back and search again
    await navigateToBookSearch(page);
    await page.fill('input[placeholder="请输入图书名称"]', TEST_BOOK_NAME);
    await page.click('button:has-text("查询")');
    await page.waitForTimeout(500);

    // 5. Verify data still correct (availableCopies=0 since reader still has it)
    const row = page.locator('.el-table__body tr', { hasText: TEST_BOOK_ISBN });
    const inventoryCell = row.locator('td').nth(6);
    const text = await inventoryCell.textContent();
    expect(text).toContain('馆藏 1');
    // After borrow, should show 已借完
    expect(text).toContain('已借完');
  });

  test('TC7: 读者点击已借过的书 → disabled + 不可重复借阅提示', async ({ page }) => {
    // 1. Reader login
    await login(page, 'reader', '123456');
    await navigateToBookSearch(page);

    // 2. Search the test book
    await page.fill('input[placeholder="请输入图书名称"]', TEST_BOOK_NAME);
    await page.click('button:has-text("查询")');
    await page.waitForTimeout(500);

    // 3. Verify borrow button is disabled (still has the book)
    const borrowBtn = page.locator('button:has-text("借阅")').first();
    await expect(borrowBtn).toBeDisabled({ timeout: 3000 });

    // 4. Click anyway — should show duplicate error
    await borrowBtn.click();
    const errorMsg = page.locator('.el-message--error');
    await expect(errorMsg).toBeVisible({ timeout: 3000 });
    const msgText = await errorMsg.textContent();
    expect(msgText).toContain('不可重复借阅');
  });
});
