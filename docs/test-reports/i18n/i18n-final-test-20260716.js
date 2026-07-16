// test-automation: i18n 全局补齐最终验收
async (page) => {
  const B = 'http://localhost:9876'; const R = [];
  const sw = async (l) => {
    const sp = page.locator('.header-row1 .lang-switch span');
    await (l==='en'?sp.last():sp.first()).click();
    await page.waitForTimeout(300);
  };
  await page.goto(B+'/dashboard',{waitUntil:'networkidle'}); await page.waitForTimeout(400);
  await sw('en');

  // TC1: EN /user
  await page.goto(B+'/user',{waitUntil:'networkidle'}); await page.waitForTimeout(500);
  const usSide = await page.locator('.search-sidebar').textContent().catch(()=>'');
  const usTbl = await page.locator('.el-table__header').textContent().catch(()=>'');
  R.push({tc:'TC1-EN用户页',pass:usSide.includes('Search')&&usTbl.includes('Reader ID')&&usTbl.includes('Actions')});

  // TC2: EN /lendrecord
  await page.goto(B+'/lendrecord',{waitUntil:'networkidle'}); await page.waitForTimeout(500);
  const lrTbl = await page.locator('.el-table__body').first().textContent().catch(()=>'');
  R.push({tc:'TC2-EN借阅记录',pass:lrTbl.includes('Unreturned')||lrTbl.includes('Returned')});

  // TC3: EN /bookwithuser status tags
  await page.goto(B+'/bookwithuser',{waitUntil:'networkidle'}); await page.waitForTimeout(500);
  const bwTags = await page.locator('.el-table__body').first().textContent().catch(()=>'');
  R.push({tc:'TC3-EN状态标签',pass:bwTags.includes('Overdue')||bwTags.includes('Normal')||bwTags.includes('Due Soon')});

  // TC4: EN /book sidebar buttons
  await page.goto(B+'/book',{waitUntil:'networkidle'}); await page.waitForTimeout(500);
  const bkSide = await page.locator('.search-sidebar').textContent().catch(()=>'');
  R.push({tc:'TC4-EN图书侧栏',pass:bkSide.includes('Search')&&bkSide.includes('Filters')});

  // TC5: EN /log sidebar
  await page.goto(B+'/log',{waitUntil:'networkidle'}); await page.waitForTimeout(500);
  const lgSide = await page.locator('.search-sidebar').textContent().catch(()=>'');
  R.push({tc:'TC5-EN日志侧栏',pass:lgSide.includes('Operation')||lgSide.includes('Operator')||lgSide.includes('Start Time')});

  // TC6: ZH restore
  await sw('zh');
  await page.goto(B+'/user',{waitUntil:'networkidle'}); await page.waitForTimeout(400);
  const zhTbl = await page.locator('.el-table__header').textContent().catch(()=>'');
  R.push({tc:'TC6-ZH恢复',pass:zhTbl.includes('读者编号')&&zhTbl.includes('姓名')});

  // TC7: No error
  R.push({tc:'TC7-无编译错误',pass:!(await page.locator('iframe').isVisible().catch(()=>true))});

  const passed = R.filter(r=>r.pass).length;
  return {results:R,passed,total:R.length,summary:`${passed}/${R.length} PASS`};
}
