#!/bin/bash
# test-automation: 删除图书孤儿数据风险修复 — API 验收测试
# 日期: 2026-07-16  目标: BookController / BookWithUserController
BASE="${1:-http://localhost:9090}"
PASS=0; FAIL=0
RED='\033[0;31m'; GREEN='\033[0;32m'; NC='\033[0m'
log_pass() { echo -e "${GREEN}PASS${NC} $1"; PASS=$((PASS+1)); }
log_fail() { echo -e "${RED}FAIL${NC} $1 — $2"; FAIL=$((FAIL+1)); }

# 登录
ADMIN_TOKEN=$(curl -s -X POST "$BASE/user/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)

echo "============================================"
echo " test-automation: 孤儿数据风险修复测试"
echo " BASE: $BASE"
echo "============================================"

# ==================== TC1: 删除被借图书拒绝 ====================
echo ""; echo "--- TC1: DELETE /book/12 (被借中) ---"
RESP=$(curl -s -X DELETE "$BASE/book/12" -H "Authorization: Bearer $ADMIN_TOKEN")
CODE=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)
MSG=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('msg',''))" 2>/dev/null)
if [ "$CODE" != "0" ] && echo "$MSG" | grep -q "借阅"; then
  log_pass "TC1 被借图书删除被拒: $MSG"
else
  log_fail "TC1" "code=$CODE msg=$MSG"
fi

# ==================== TC2: 删除未借图书成功 ====================
echo ""; echo "--- TC2: 删除未借图书 ---"
# 创建临时测试书
TS=$(date +%s)
curl -s -X POST "$BASE/book" -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"isbn\":\"FIX-TEST-$TS\",\"name\":\"修复测试书\",\"price\":1,\"totalCopies\":1,\"availableCopies\":1}" > /dev/null
BOOK_ID=$(curl -s -G "$BASE/book" --data-urlencode "search1=FIX-TEST-$TS" -H "Authorization: Bearer $ADMIN_TOKEN" \
  | python3 -c "import sys,json;r=json.load(sys.stdin)['data']['records'];print(r[0]['id'] if r else '')" 2>/dev/null)

RESP=$(curl -s -X DELETE "$BASE/book/$BOOK_ID" -H "Authorization: Bearer $ADMIN_TOKEN")
CODE=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)
if [ "$CODE" = "0" ]; then
  log_pass "TC2 未借图书删除成功"
else
  log_fail "TC2" "code=$CODE $(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('msg',''))" 2>/dev/null)"
fi

# ==================== TC3: 直接删活跃借阅拒绝 ====================
echo ""; echo "--- TC3: POST /bookwithuser/deleteRecords (活跃借阅) ---"
RESP=$(curl -s -X POST "$BASE/bookwithuser/deleteRecords" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '[{"isbn":"3213123123","userId":18}]')
CODE=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)
MSG=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('msg',''))" 2>/dev/null)
if [ "$CODE" != "0" ] && echo "$MSG" | grep -q "还书"; then
  log_pass "TC3 直接删活跃借阅被拒: $MSG"
else
  log_fail "TC3" "code=$CODE msg=$MSG"
fi

# ==================== TC4: 拒绝后数据不变 ====================
echo ""; echo "--- TC4: 拒绝后 bookwithuser + book 不变 ---"
BW_COUNT=$(curl -s -G "$BASE/bookwithuser" --data-urlencode "search1=3213123123" -H "Authorization: Bearer $ADMIN_TOKEN" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['total'])" 2>/dev/null)
BOOK_AVAIL=$(curl -s -G "$BASE/book" --data-urlencode "search1=3213123123" -H "Authorization: Bearer $ADMIN_TOKEN" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['records'][0]['availableCopies'])" 2>/dev/null)
if [ "$BW_COUNT" != "0" ] && [ "$BOOK_AVAIL" = "0" ]; then
  log_pass "TC4 数据不变: bookwithuser=$BW_COUNT available=$BOOK_AVAIL"
else
  log_fail "TC4" "bookwithuser=$BW_COUNT available=$BOOK_AVAIL"
fi

# ==================== TC5: deleteRecord 端点已删除 ====================
echo ""; echo "--- TC5: POST /bookwithuser/deleteRecord (已删除) ---"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/bookwithuser/deleteRecord" \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_TOKEN" -d '{}')
if [ "$HTTP_CODE" = "404" ]; then
  log_pass "TC5 deleteRecord 端点已删除 HTTP $HTTP_CODE"
else
  log_fail "TC5" "HTTP $HTTP_CODE (expected 404)"
fi

# ==================== TC6: 批量删除含被借图书拒绝 ====================
echo ""; echo "--- TC6: POST /book/deleteBatch (含被借图书) ---"
RESP=$(curl -s -X POST "$BASE/book/deleteBatch" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" -d '[12, 13]')
CODE=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)
MSG=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('msg',''))" 2>/dev/null)
if [ "$CODE" != "0" ] && echo "$MSG" | grep -q "借阅"; then
  log_pass "TC6 批量删除被拒: $MSG"
else
  log_fail "TC6" "code=$CODE msg=$MSG"
fi

# ==================== 汇总 ====================
echo ""; echo "============================================"
if [ $FAIL -eq 0 ]; then
  echo -e " ${GREEN}全部通过${NC}: $PASS/$PASS"
  echo "============================================"; exit 0
else
  echo -e " ${RED}存在失败${NC}: PASS=$PASS FAIL=$FAIL"
  echo "============================================"; exit 1
fi
