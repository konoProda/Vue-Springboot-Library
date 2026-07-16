#!/bin/bash
# ============================================================
# 借书/还书/续借 后端流程验证脚本
#
# 运行环境: bash, curl, python3 (标准库 only)
# 执行命令: bash docs/verify-borrow-flow.sh [BASE_URL]
# 默认 BASE_URL: http://localhost:9090
#
# 黑盒验收约定:
#   - 不直接连接数据库，仅通过 HTTP 接口操作和查询
#   - 自动准备带唯一标识的测试数据，结束后清理
#   - 每个用例输出 PASS 或 FAIL + 实际结果 + 失败原因
#   - 任一用例失败时返回非零退出码
# ============================================================

set -e

BASE_URL="${1:-http://localhost:9090}"
PASS=0
FAIL=0
TS=$(date +%s)
TEST_ISBN="VERIFY-${TS}"
TEST_BOOK_NAME="验证测试书-${TS}"
READER_TOKEN=""
ADMIN_TOKEN=""
READER_USERNAME="reader"
READER_PASSWORD="123456"
ADMIN_USERNAME="admin"
ADMIN_PASSWORD="123456"

# ==================== 工具函数 ====================

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

log_pass() { echo -e "${GREEN}PASS${NC} $1"; PASS=$((PASS+1)); }
log_fail() { echo -e "${RED}FAIL${NC} $1 — $2"; FAIL=$((FAIL+1)); }

# 查询借阅状态 (当前借阅 bookwithuser)
query_active_borrow() {
    local isbn="$1"
    curl -s "${BASE_URL}/bookwithuser?search1=${isbn}" \
         -H "Authorization: Bearer ${ADMIN_TOKEN}" 2>/dev/null
}

# 查询借阅历史 (lend_record)
query_lend_history() {
    local isbn="$1"
    curl -s "${BASE_URL}/LendRecord?search1=${isbn}" \
         -H "Authorization: Bearer ${ADMIN_TOKEN}" 2>/dev/null
}

# 查询图书信息
query_book() {
    local isbn="$1"
    curl -s "${BASE_URL}/book?search1=${isbn}" \
         -H "Authorization: Bearer ${ADMIN_TOKEN}" 2>/dev/null
}

# 提取 JSON 字段
json_field() { python3 -c "import sys,json; d=json.load(sys.stdin); print(d${1})" 2>/dev/null; }

# ==================== 登录 ====================

echo "============================================"
echo " 借/还/续 后端流程验证"
echo " BASE_URL: ${BASE_URL}"
echo " 测试 ISBN: ${TEST_ISBN}"
echo "============================================"
echo ""

echo "--- 登录 ---"
ADMIN_TOKEN=$(curl -s -X POST "${BASE_URL}/user/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${ADMIN_USERNAME}\",\"password\":\"${ADMIN_PASSWORD}\"}" \
    | json_field '["data"]["token"]')
READER_TOKEN=$(curl -s -X POST "${BASE_URL}/user/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${READER_USERNAME}\",\"password\":\"${READER_PASSWORD}\"}" \
    | json_field '["data"]["token"]')

if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "null" ]; then
    log_fail "管理员登录" "无法获取token"; exit 1
fi
if [ -z "$READER_TOKEN" ] || [ "$READER_TOKEN" = "null" ]; then
    log_fail "读者登录" "无法获取token"; exit 1
fi
echo "  admin token: ${ADMIN_TOKEN:0:20}..."
echo "  reader token: ${READER_TOKEN:0:20}..."

# ==================== 准备测试数据 ====================

echo ""
echo "--- 准备测试数据 ---"

# 创建一本测试图书 (totalCopies=1)
curl -s -X POST "${BASE_URL}/book" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -d "{\"isbn\":\"${TEST_ISBN}\",\"name\":\"${TEST_BOOK_NAME}\",\"price\":9.9,\"author\":\"验证作者\",\"publisher\":\"验证出版社\",\"totalCopies\":1,\"availableCopies\":1,\"borrownum\":0}" \
    > /dev/null

# 确认图书已创建
BOOK_INFO=$(query_book "${TEST_ISBN}")
BOOK_AVAILABLE=$(echo "$BOOK_INFO" | json_field '["data"]["records"][0]["availableCopies"]')
BOOK_TOTAL=$(echo "$BOOK_INFO" | json_field '["data"]["records"][0]["totalCopies"]')
BOOK_ID=$(echo "$BOOK_INFO" | json_field '["data"]["records"][0]["id"]')
echo "  测试图书: isbn=${TEST_ISBN}, id=${BOOK_ID}, available=${BOOK_AVAILABLE}, total=${BOOK_TOTAL}"

# ==================== TC1: 可借图书借阅成功 ====================

echo ""
echo "=== TC1: 可借图书借阅成功 ==="

# 借阅前快照
PRE_AVAIL=$(echo "$BOOK_INFO" | json_field '["data"]["records"][0]["availableCopies"]')
PRE_ACTIVE_COUNT=$(query_active_borrow "${TEST_ISBN}" | json_field '["data"]["total"]')
PRE_HISTORY_COUNT=$(query_lend_history "${TEST_ISBN}" | json_field '["data"]["total"]')

# 发起借阅
BORROW_RESP=$(curl -s -X POST "${BASE_URL}/borrow" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer ${READER_TOKEN}" \
    -d "{\"isbn\":\"${TEST_ISBN}\"}")
BORROW_CODE=$(echo "$BORROW_RESP" | json_field '["code"]')

# 借阅后查询
POST_BOOK=$(query_book "${TEST_ISBN}")
POST_AVAIL=$(echo "$POST_BOOK" | json_field '["data"]["records"][0]["availableCopies"]')
POST_ACTIVE_COUNT=$(query_active_borrow "${TEST_ISBN}" | json_field '["data"]["total"]')
POST_HISTORY_COUNT=$(query_lend_history "${TEST_ISBN}" | json_field '["data"]["total"]')
POST_DEADTIME=$(query_active_borrow "${TEST_ISBN}" | json_field '["data"]["records"][0]["deadtime"]')

if [ "$BORROW_CODE" != "0" ]; then
    log_fail "TC1" "借阅失败: $(echo "$BORROW_RESP" | json_field '["msg"]')"
elif [ "$POST_AVAIL" != "0" ]; then
    log_fail "TC1" "库存未减少: available=${POST_AVAIL} (预期 0)"
elif [ "$POST_ACTIVE_COUNT" != "1" ]; then
    log_fail "TC1" "当前借阅数量异常: ${POST_ACTIVE_COUNT} (预期 1)"
elif [ "$POST_HISTORY_COUNT" != "1" ]; then
    log_fail "TC1" "历史记录数量异常: ${POST_HISTORY_COUNT} (预期 1)"
else
    log_pass "TC1 库存减少=${POST_AVAIL}, 当前借阅=${POST_ACTIVE_COUNT}, 历史记录=${POST_HISTORY_COUNT}"
fi

# ==================== TC2: 库存为0时借阅失败 ====================

echo ""
echo "=== TC2: 库存为0时借阅失败 ==="

PRE2_AVAIL=$(echo "$POST_BOOK" | json_field '["data"]["records"][0]["availableCopies"]')
PRE2_ACTIVE=$(query_active_borrow "${TEST_ISBN}" | json_field '["data"]["total"]')
PRE2_HISTORY=$(query_lend_history "${TEST_ISBN}" | json_field '["data"]["total"]')

# 读者B尝试借阅
READER2_TOKEN=$(curl -s -X POST "${BASE_URL}/user/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"reader2","password":"123456"}' \
    | json_field '["data"]["token"]')

BORROW2_RESP=$(curl -s -X POST "${BASE_URL}/borrow" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer ${READER2_TOKEN}" \
    -d "{\"isbn\":\"${TEST_ISBN}\"}")
BORROW2_CODE=$(echo "$BORROW2_RESP" | json_field '["code"]')

POST2_AVAIL=$(query_book "${TEST_ISBN}" | json_field '["data"]["records"][0]["availableCopies"]')
POST2_ACTIVE=$(query_active_borrow "${TEST_ISBN}" | json_field '["data"]["total"]')
POST2_HISTORY=$(query_lend_history "${TEST_ISBN}" | json_field '["data"]["total"]')

if [ "$BORROW2_CODE" = "0" ]; then
    log_fail "TC2" "借阅不应该成功 (库存为0)"
elif [ "$POST2_AVAIL" != "$PRE2_AVAIL" ]; then
    log_fail "TC2" "库存发生变化: ${PRE2_AVAIL} → ${POST2_AVAIL}"
elif [ "$POST2_ACTIVE" != "$PRE2_ACTIVE" ]; then
    log_fail "TC2" "当前借阅数量变化: ${PRE2_ACTIVE} → ${POST2_ACTIVE}"
elif [ "$POST2_HISTORY" != "$PRE2_HISTORY" ]; then
    log_fail "TC2" "历史记录数量变化: ${PRE2_HISTORY} → ${POST2_HISTORY}"
else
    log_pass "TC2 借阅失败(code=${BORROW2_CODE}), 库存/记录未变"
fi

# ==================== TC3: 首次续借成功 ====================

echo ""
echo "=== TC3: 首次续借成功 ==="

PRE3_DEADTIME=$(query_active_borrow "${TEST_ISBN}" | json_field '["data"]["records"][0]["deadtime"]')
PRE3_PROLONG=$(query_active_borrow "${TEST_ISBN}" | json_field '["data"]["records"][0]["prolong"]')

RENEW_RESP=$(curl -s -X POST "${BASE_URL}/renew" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer ${READER_TOKEN}" \
    -d "{\"isbn\":\"${TEST_ISBN}\"}")
RENEW_CODE=$(echo "$RENEW_RESP" | json_field '["code"]')

POST3_DEADTIME=$(query_active_borrow "${TEST_ISBN}" | json_field '["data"]["records"][0]["deadtime"]')
POST3_PROLONG=$(query_active_borrow "${TEST_ISBN}" | json_field '["data"]["records"][0]["prolong"]')

# 验证日期延长约30天
PRE3_TS=$(date -d "${PRE3_DEADTIME}" +%s 2>/dev/null || date -j -f "%Y-%m-%d %H:%M:%S" "${PRE3_DEADTIME}" +%s 2>/dev/null || echo "0")
POST3_TS=$(date -d "${POST3_DEADTIME}" +%s 2>/dev/null || date -j -f "%Y-%m-%d %H:%M:%S" "${POST3_DEADTIME}" +%s 2>/dev/null || echo "0")
DIFF_DAYS=$(( (POST3_TS - PRE3_TS) / 86400 ))

if [ "$RENEW_CODE" != "0" ]; then
    log_fail "TC3" "续借失败: $(echo "$RENEW_RESP" | json_field '["msg"]')"
elif [ "$POST3_PROLONG" != "0" ]; then
    log_fail "TC3" "续借次数未减为0: ${POST3_PROLONG}"
elif [ "$DIFF_DAYS" -lt 28 ] || [ "$DIFF_DAYS" -gt 32 ]; then
    log_fail "TC3" "应还日期延长天数异常: ${DIFF_DAYS}天 (预期 ~30天)"
else
    log_pass "TC3 续借成功, 延长${DIFF_DAYS}天, 剩余续借次数=${POST3_PROLONG}"
fi

# ==================== TC4: 再次续借失败 ====================

echo ""
echo "=== TC4: 再次续借失败 ==="

PRE4_DEADTIME=$(query_active_borrow "${TEST_ISBN}" | json_field '["data"]["records"][0]["deadtime"]')

RENEW2_RESP=$(curl -s -X POST "${BASE_URL}/renew" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer ${READER_TOKEN}" \
    -d "{\"isbn\":\"${TEST_ISBN}\"}")
RENEW2_CODE=$(echo "$RENEW2_RESP" | json_field '["code"]')

POST4_DEADTIME=$(query_active_borrow "${TEST_ISBN}" | json_field '["data"]["records"][0]["deadtime"]')

if [ "$RENEW2_CODE" = "0" ]; then
    log_fail "TC4" "第二次续借不应该成功"
elif [ "$POST4_DEADTIME" != "$PRE4_DEADTIME" ]; then
    log_fail "TC4" "应还日期发生变化: ${PRE4_DEADTIME} → ${POST4_DEADTIME}"
else
    log_pass "TC4 续借失败(code=${RENEW2_CODE}), 应还日期不变"
fi

# ==================== TC5: 正常还书成功 ====================

echo ""
echo "=== TC5: 正常还书成功 ==="

PRE5_AVAIL=$(query_book "${TEST_ISBN}" | json_field '["data"]["records"][0]["availableCopies"]')
PRE5_ACTIVE=$(query_active_borrow "${TEST_ISBN}" | json_field '["data"]["total"]')

RETURN_RESP=$(curl -s -X POST "${BASE_URL}/return" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer ${READER_TOKEN}" \
    -d "{\"isbn\":\"${TEST_ISBN}\"}")
RETURN_CODE=$(echo "$RETURN_RESP" | json_field '["code"]')

POST5_AVAIL=$(query_book "${TEST_ISBN}" | json_field '["data"]["records"][0]["availableCopies"]')
POST5_ACTIVE=$(query_active_borrow "${TEST_ISBN}" | json_field '["data"]["total"]')
POST5_HISTORY=$(query_lend_history "${TEST_ISBN}" | json_field '["data"]["total"]')

# 检查历史记录中最新一条的归还状态
LATEST_STATUS=$(query_lend_history "${TEST_ISBN}" | json_field '["data"]["records"][0]["status"]')

if [ "$RETURN_CODE" != "0" ]; then
    log_fail "TC5" "还书失败: $(echo "$RETURN_RESP" | json_field '["msg"]')"
elif [ "$POST5_AVAIL" != "1" ]; then
    log_fail "TC5" "库存未恢复: available=${POST5_AVAIL} (预期 1)"
elif [ "$POST5_ACTIVE" != "0" ]; then
    log_fail "TC5" "当前借阅未清理: ${POST5_ACTIVE} (预期 0)"
elif [ "$LATEST_STATUS" != "1" ]; then
    log_fail "TC5" "历史记录未标记已归还: status=${LATEST_STATUS}"
else
    log_pass "TC5 库存恢复=${POST5_AVAIL}, 借阅清理, 历史status=${LATEST_STATUS}"
fi

# ==================== TC6: 重复还书失败 ====================

echo ""
echo "=== TC6: 重复还书失败 ==="

PRE6_AVAIL=$(query_book "${TEST_ISBN}" | json_field '["data"]["records"][0]["availableCopies"]')

RETURN2_RESP=$(curl -s -X POST "${BASE_URL}/return" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer ${READER_TOKEN}" \
    -d "{\"isbn\":\"${TEST_ISBN}\"}")
RETURN2_CODE=$(echo "$RETURN2_RESP" | json_field '["code"]')

POST6_AVAIL=$(query_book "${TEST_ISBN}" | json_field '["data"]["records"][0]["availableCopies"]')

if [ "$RETURN2_CODE" = "0" ]; then
    log_fail "TC6" "重复还书不应该成功"
elif [ "$POST6_AVAIL" != "$PRE6_AVAIL" ]; then
    log_fail "TC6" "库存发生变化: ${PRE6_AVAIL} → ${POST6_AVAIL}"
else
    log_pass "TC6 还书失败(code=${RETURN2_CODE}), 库存不变=${POST6_AVAIL}"
fi

# ==================== 清理 ====================

echo ""
echo "--- 清理测试数据 ---"

# 再次借书以便删除 (需要先借出，因为 deleteBatch 需要在活跃借阅中删除)
# 简单方式：直接通过 admin 删除图书
curl -s -X DELETE "${BASE_URL}/book/${BOOK_ID}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" > /dev/null
echo "  已删除测试图书 ${TEST_ISBN}"

# ==================== 结果汇总 ====================

echo ""
echo "============================================"
if [ $FAIL -eq 0 ]; then
    echo -e " ${GREEN}全部通过${NC}: ${PASS}/${PASS}"
    echo "============================================"
    exit 0
else
    echo -e " ${RED}存在失败${NC}: 通过=${PASS}, 失败=${FAIL}"
    echo "============================================"
    exit 1
fi
