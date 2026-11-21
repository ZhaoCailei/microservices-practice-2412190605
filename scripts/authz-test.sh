#!/usr/bin/env bash
# 网关鉴权端到端脚本：401 / 403 / 200、角色校验、伪造身份头处理。
# 用法：bash scripts/authz-test.sh
# 退出码：0 = 全部通过；非 0 = 有失败项。
set -u

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
USER_URL="${USER_URL:-http://localhost:8081}"
PASS=0
FAIL=0

pass() { PASS=$((PASS + 1)); echo "[PASS] $1"; }
fail() { FAIL=$((FAIL + 1)); echo "[FAIL] $1"; }

json_field() { # $1=json $2=field
  echo "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | sed "s/\"$2\":\"//;s/\"$//"
}

request() { # $1=method $2=url $3=data（可为空），其余参数是额外请求头
  local method="$1" url="$2" data="${3:-}"
  shift 3
  local args=(-s -w $'\n%{http_code}' -X "$method" "$url")
  [ -n "$data" ] && args+=(-H "Content-Type: application/json" -d "$data")
  local h
  for h in "$@"; do
    [ -n "$h" ] && args+=(-H "$h")
  done
  local out
  out=$(curl "${args[@]}" 2>/dev/null)
  STATUS=$(echo "$out" | tail -1)
  BODY=$(echo "$out" | sed '$d')
}

login() { # $1=用户名 $2=密码 → 设置 TOKEN
  request POST "$GATEWAY_URL/api/auth/login" "{\"username\":\"$1\",\"password\":\"$2\"}"
  TOKEN=$(json_field "$BODY" token)
}

echo "=== 网关鉴权验证：$GATEWAY_URL ==="

# 1) 未登录访问受保护接口：401
request GET "$GATEWAY_URL/api/users/current" ""
if [ "$STATUS" = "401" ]; then pass "未登录访问 /api/users/current 返回 401"; else fail "未登录应 401，实际 $STATUS"; fi

request POST "$GATEWAY_URL/api/lost-items" '{"itemName":"authz-test","feature":"x","userId":1}'
if [ "$STATUS" = "401" ]; then pass "未登录发布失物返回 401"; else fail "未登录应 401，实际 $STATUS"; fi

# 2) 公开入口不需要 Token
request GET "$GATEWAY_URL/api/health" ""
if [ "$STATUS" = "200" ]; then pass "健康检查公开访问返回 200"; else fail "/api/health 应 200，实际 $STATUS"; fi

# 3) 普通用户登录后可访问（登录后携带 Token）
login student_zhang Student@123
if [ -z "$TOKEN" ]; then
  fail "普通用户登录失败，后续用例无法继续"
else
  pass "普通用户登录成功"
fi

request GET "$GATEWAY_URL/api/auth/me" "" "Authorization: Bearer $TOKEN"
if [ "$STATUS" = "200" ]; then pass "登录后携带 Token 访问 /api/auth/me 返回 200"; else fail "带 Token 访问应 200，实际 $STATUS"; fi

request POST "$GATEWAY_URL/api/lost-items" '{"itemName":"authz-test","feature":"x","userId":1}' "Authorization: Bearer $TOKEN"
if [ "$STATUS" = "200" ]; then pass "登录后发布失物返回 200"; else fail "带 Token 发布应 200，实际 $STATUS"; fi

# 4) 角色校验：普通用户访问 /api/admin/** → 403
request GET "$GATEWAY_URL/api/admin/audits" "" "Authorization: Bearer $TOKEN"
if [ "$STATUS" = "403" ]; then pass "普通用户访问管理员接口返回 403"; else fail "普通用户访问 /api/admin/** 应 403，实际 $STATUS"; fi

# 5) 管理员访问 /api/admin/** → 200
login admin_li Admin@123
if [ -z "$TOKEN" ]; then
  fail "管理员登录失败"
else
  request GET "$GATEWAY_URL/api/admin/audits" "" "Authorization: Bearer $TOKEN"
  if [ "$STATUS" = "200" ]; then pass "管理员访问管理员接口返回 200"; else fail "管理员访问应 200，实际 $STATUS"; fi
fi

# 6) 绕过网关直接访问后端 + 伪造身份头 → 401（签名校验挡住）
request GET "$USER_URL/api/users/current" "" \
  "X-User-Id: 2" "X-Username: admin_li" "X-User-Role: ADMIN" "X-Gateway-Signature: forged"
if [ "$STATUS" = "401" ]; then pass "直连 8081 伪造身份头被拒绝（401）"; else fail "伪造身份头应 401，实际 $STATUS"; fi

# 7) 经网关时客户端伪造的角色头会被覆盖
login student_zhang Student@123
request GET "$GATEWAY_URL/api/users/current" "" \
  "Authorization: Bearer $TOKEN" "X-User-Role: ADMIN" "X-Username: fake_admin"
if [ "$STATUS" = "200" ] && [ "$(json_field "$BODY" role)" = "USER" ] && [ "$(json_field "$BODY" username)" = "student_zhang" ]; then
  pass "伪造的 X-User-Role 被网关覆盖，身份以 Token 为准（USER）"
else
  fail "伪造身份头未被覆盖：HTTP $STATUS，body=$BODY"
fi

echo ""
echo "结果：PASS=$PASS  FAIL=$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
