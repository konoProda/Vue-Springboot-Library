#!/bin/bash
# ============================================================
# test-automation: 管理员新增和删除用户 — API 验收测试
# 日期: 2026-07-15
# 目标: UserController (POST /user, DELETE /user/{id})
# ============================================================
# 不使用 set -e，手动处理错误
trap 'echo "ERROR at line $LINENO"' ERR

BASE="${1:-http://localhost:9090}"
TS=$(date +%s)
TEST_USER="testreader-${TS}"
TEST_PASS="test123"
PASS=0; FAIL=0

RED='\033[0;31m'; GREEN='\033[0;32m'; NC='\033[0m'
log_pass() { echo -e "${GREEN}PASS${NC} $1"; PASS=$((PASS+1)); }
log_fail() { echo -e "${RED}FAIL${NC} $1 — $2"; FAIL=$((FAIL+1)); }

echo "============================================"
echo " test-automation: 用户CRUD API测试"
echo " BASE: $BASE  测试用户: $TEST_USER"
echo "============================================"

# 登录管理员
ADMIN_TOKEN=$(curl -s -X POST "$BASE/user/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)

# ==================== TC1: 新增临时读者 ====================
echo ""; echo "--- TC1: 新增临时读者 ---"
RESP=$(curl -s -X POST "$BASE/user" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASS\",\"nickName\":\"临时读者-$TS\",\"phone\":\"13800000000\",\"sex\":\"男\"}")
CODE=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)
if [ "$CODE" = "0" ]; then log_pass "TC1 新增成功 code=$CODE"
else log_fail "TC1" "code=$CODE $(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('msg',''))" 2>/dev/null)"; fi

# 获取新用户ID：通过 /user/usersearch 按昵称搜索
USER_ID=""
for i in $(seq 1 3); do
  USER_ID=$(curl -s -G "$BASE/user/usersearch" \
    --data-urlencode "search2=临时读者-$TS" \
    --data-urlencode "pageSize=5" \
    -H "Authorization: Bearer $ADMIN_TOKEN" | python3 -c "
import sys,json
try:
  recs=json.load(sys.stdin).get('data',{}).get('records',[])
  print(recs[0]['id'] if recs else '')
except: pass
" 2>/dev/null)
  [ -n "$USER_ID" ] && break
  sleep 1
done
if [ -z "$USER_ID" ]; then
  echo "WARN: 无法查询到用户ID，跳过后续依赖该ID的测试"
fi

# ==================== TC2: 新用户登录 ====================
echo ""; echo "--- TC2: 新用户登录 ---"
RESP=$(curl -s -X POST "$BASE/user/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASS\"}")
CODE=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)
TEST_TOKEN=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)
if [ "$CODE" = "0" ]; then log_pass "TC2 登录成功 获取token"
else log_fail "TC2" "code=$CODE"; fi

# ==================== TC3: 重复用户名新增失败 ====================
echo ""; echo "--- TC3: 重复用户名新增失败 ---"
RESP=$(curl -s -X POST "$BASE/user" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASS\",\"nickName\":\"重复测试\"}")
CODE=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)
MSG=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('msg',''))" 2>/dev/null)
if [ "$CODE" != "0" ]; then log_pass "TC3 新增失败 code=$CODE msg=$MSG"
else log_fail "TC3" "重复用户名不应成功 code=$CODE"; fi

# ==================== TC4: 有未归还图书时删除失败 ====================
echo ""; echo "--- TC4: 有未归还图书时删除失败 ---"
# 测试用户借书
BORROW_RESP=$(curl -s -X POST "$BASE/borrow" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TEST_TOKEN" -d '{"isbn":"TEST-INV-001"}')
BORROW_CODE=$(echo "$BORROW_RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)
echo "  借书结果: code=$BORROW_CODE"

# 尝试删除
RESP=$(curl -s -X DELETE "$BASE/user/$USER_ID" -H "Authorization: Bearer $ADMIN_TOKEN")
CODE=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)
MSG=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('msg',''))" 2>/dev/null)
if [ "$CODE" != "0" ]; then log_pass "TC4 删除被拒 code=$CODE msg=$MSG"
else log_fail "TC4" "有未归还图书时不应允许删除"; fi

# ==================== TC5: 归还后删除成功 ====================
echo ""; echo "--- TC5: 归还后删除成功 ---"
RETURN_RESP=$(curl -s -X POST "$BASE/return" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TEST_TOKEN" -d '{"isbn":"TEST-INV-001"}')
RETURN_CODE=$(echo "$RETURN_RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)
echo "  还书结果: code=$RETURN_CODE"

sleep 1
RESP=$(curl -s -X DELETE "$BASE/user/$USER_ID" -H "Authorization: Bearer $ADMIN_TOKEN")
CODE=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)
if [ "$CODE" = "0" ]; then log_pass "TC5 删除成功 code=$CODE"
else log_fail "TC5" "code=$CODE $(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('msg',''))" 2>/dev/null)"; fi

# ==================== TC6: 被删除用户无法登录 ====================
echo ""; echo "--- TC6: 被删除用户无法登录 ---"
RESP=$(curl -s -X POST "$BASE/user/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$TEST_USER\",\"password\":\"$TEST_PASS\"}")
CODE=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)
if [ "$CODE" != "0" ]; then log_pass "TC6 登录失败 code=$CODE"
else log_fail "TC6" "已被删除的用户不应能登录"; fi

# ==================== 汇总 ====================
echo ""; echo "============================================"
if [ $FAIL -eq 0 ]; then
  echo -e " ${GREEN}全部通过${NC}: $PASS/$PASS"
  echo "============================================"; exit 0
else
  echo -e " ${RED}存在失败${NC}: PASS=$PASS FAIL=$FAIL"
  echo "============================================"; exit 1
fi
