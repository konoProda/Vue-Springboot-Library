#!/bin/bash
# 后端 API 综合验证 (B1-B6)
BASE="${1:-http://localhost:9090}"
PASS=0; FAIL=0
log_pass() { echo -e "\033[0;32mPASS\033[0m $1"; PASS=$((PASS+1)); }
log_fail() { echo -e "\033[0;31mFAIL\033[0m $1 - $2"; FAIL=$((FAIL+1)); }
TOKEN=$(curl -s -X POST "$BASE/user/login" -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)
READER_TOKEN=$(curl -s -X POST "$BASE/user/login" -H 'Content-Type: application/json' -d '{"username":"reader","password":"123456"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)

echo "============================================"
echo " Backend API 综合验证"
echo "============================================"

# B1: 验证脚本
echo ""; echo "--- B1: verify-borrow-flow.sh ---"
bash /home/sck/scktest/claude_test/Vue-Springboot-Library/docs/backend-flow-refactor/verify-borrow-flow.sh "$BASE" > /dev/null 2>&1
[ $? -eq 0 ] && log_pass "B1 借还续6用例" || log_fail "B1" "脚本失败"

# B2: 用户CRUD
echo ""; echo "--- B2: 用户CRUD ---"
TS=$(date +%s)
R=$(curl -s -X POST "$BASE/user" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" -d "{\"username\":\"vfy-$TS\",\"password\":\"test\",\"nickName\":\"验证用户\"}")
[ "$(echo $R | python3 -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)" = "0" ] && log_pass "B2a 新增用户" || log_fail "B2a" "$R"
R=$(curl -s -X POST "$BASE/user" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" -d "{\"username\":\"vfy-$TS\",\"password\":\"test\"}")
[ "$(echo $R | python3 -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)" != "0" ] && log_pass "B2b 重复拒绝" || log_fail "B2b" "$R"
R=$(curl -s -X POST "$BASE/user/login" -H 'Content-Type: application/json' -d "{\"username\":\"vfy-$TS\",\"password\":\"test\"}")
[ "$(echo $R | python3 -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)" = "0" ] && log_pass "B2c 新用户登录" || log_fail "B2c" "$R"

# 未归还图书删除拒绝 (reader id=18 has active borrows)
R=$(curl -s -X DELETE "$BASE/user/18" -H "Authorization: Bearer $TOKEN")
[ "$(echo $R | python3 -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)" != "0" ] && log_pass "B2d 有借阅删除拒绝" || log_fail "B2d" "$R"

# B3: 图书删除防护
echo ""; echo "--- B3: 图书删除防护 ---"
R=$(curl -s -X DELETE "$BASE/book/12" -H "Authorization: Bearer $TOKEN")
[ "$(echo $R | python3 -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)" != "0" ] && log_pass "B3a 被借图书删除拒绝" || log_fail "B3a" "$R"

# B4: BW 筛选
echo ""; echo "--- B4: BW overdueFilter ---"
R=$(curl -s "$BASE/bookwithuser?overdueFilter=1&pageSize=2" -H "Authorization: Bearer $TOKEN")
BW1=$(echo $R | python3 -c "import sys,json;r=json.load(sys.stdin)['data']['records'];print(all(x['status']=='已逾期' for x in r) if r else 'EMPTY')" 2>/dev/null)
[ "$BW1" = "True" ] || [ "$BW1" = "EMPTY" ] && log_pass "B4a overdueFilter=1" || log_fail "B4a" "$BW1"
R=$(curl -s "$BASE/bookwithuser?overdueFilter=2&pageSize=2" -H "Authorization: Bearer $TOKEN")
BW2=$(echo $R | python3 -c "import sys,json;r=json.load(sys.stdin)['data']['records'];print(all(x['status']!='已逾期' for x in r) if r else 'EMPTY')" 2>/dev/null)
[ "$BW2" = "True" ] || [ "$BW2" = "EMPTY" ] && log_pass "B4b overdueFilter=2" || log_fail "B4b" "$BW2"

# B5: LR 排序
echo ""; echo "--- B5: LendRecord 排序 ---"
R=$(curl -s "$BASE/LendRecord?pageSize=2" -H "Authorization: Bearer $TOKEN")
ST=$(echo $R | python3 -c "import sys,json;r=json.load(sys.stdin)['data']['records'];print(r[0]['status'] if r else 'EMPTY')" 2>/dev/null)
[ "$ST" = "0" ] && log_pass "B5 未归还靠前" || log_fail "B5" "first status=$ST"

# B6: 读者权限
echo ""; echo "--- B6: 读者权限 ---"
# 读者更新自己
R=$(curl -s -X PUT "$BASE/user" -H 'Content-Type: application/json' -H "Authorization: Bearer $READER_TOKEN" -d '{"id":18,"username":"reader","nickName":"读者","phone":"13900000000","sex":"女"}')
[ "$(echo $R | python3 -c "import sys,json;print(json.load(sys.stdin)['code'])" 2>/dev/null)" = "0" ] && log_pass "B6a 读者更新自己" || log_fail "B6a" "$R"
# 读者只能看自己的记录
R=$(curl -s "$BASE/LendRecord?pageSize=3" -H "Authorization: Bearer $READER_TOKEN")
OK=$(echo $R | python3 -c "import sys,json;r=json.load(sys.stdin)['data']['records'];print(all(x['readerId']=='18' for x in r))" 2>/dev/null)
[ "$OK" = "True" ] && log_pass "B6b 读者只看到自己" || log_fail "B6b" "$OK"

echo ""; echo "============================================"
[ $FAIL -eq 0 ] && echo -e "\033[0;32mBackend 全部通过: $PASS/$PASS\033[0m" || echo -e "\033[0;31mBackend: PASS=$PASS FAIL=$FAIL\033[0m"
exit $FAIL
