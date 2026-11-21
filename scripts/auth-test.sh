#!/usr/bin/env bash
# 认证服务端到端脚本：登录 → 签发 JWT → 验签；同时验证 401 分支与密码哈希存储。
# 用法：bash scripts/auth-test.sh
# 退出码：0 = 全部通过；非 0 = 有失败项。
set -u

BASE_URL="${AUTH_BASE_URL:-http://localhost:8081}"
USER_URL="${USER_URL:-http://localhost:8081}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
PASS=0
FAIL=0

pass() { PASS=$((PASS + 1)); echo "[PASS] $1"; }
fail() { FAIL=$((FAIL + 1)); echo "[FAIL] $1"; }

json_field() { # $1=json $2=field（只处理顶层字符串字段）
  echo "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | sed "s/\"$2\":\"//;s/\"$//"
}

request() { # $1=method $2=url $3=data，其余参数为请求头
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

echo "=== 认证服务验证：$BASE_URL ==="

# 1) 登录成功：200 + 三段式 JWT + 角色 USER
request POST "$BASE_URL/api/auth/login" '{"username":"student_zhang","password":"Student@123"}'
TOKEN=$(json_field "$BODY" token)
if [ "$STATUS" = "200" ] && [ -n "$TOKEN" ] && [ "$(echo "$TOKEN" | awk -F. '{print NF}')" = "3" ]; then
  pass "普通用户登录成功，返回三段式 JWT"
else
  fail "普通用户登录失败：HTTP $STATUS，body=$BODY"
fi
if [ "$STATUS" = "200" ] && [ "$(json_field "$BODY" role)" = "USER" ]; then
  pass "普通用户登录返回 role=USER"
else
  fail "普通用户角色异常：HTTP $STATUS，body=$BODY"
fi

# 2) 密码错误：401
request POST "$BASE_URL/api/auth/login" '{"username":"student_zhang","password":"wrong-password"}'
if [ "$STATUS" = "401" ]; then pass "错误密码返回 401"; else fail "错误密码应为 401，实际 $STATUS"; fi

# 3) 用户不存在：401
request POST "$BASE_URL/api/auth/login" '{"username":"nobody_xyz","password":"whatever"}'
if [ "$STATUS" = "401" ]; then pass "不存在的用户返回 401"; else fail "不存在用户应为 401，实际 $STATUS"; fi

# 4) 带 Token 访问 /me：200 + 身份信息（验签通过）
if [ -n "$TOKEN" ]; then
  request GET "$BASE_URL/api/auth/me" "" "Authorization: Bearer $TOKEN"
  if [ "$STATUS" = "200" ] && [ "$(json_field "$BODY" username)" = "student_zhang" ]; then
    pass "/me 验签通过，返回 username=student_zhang"
  else
    fail "/me 应返回 200 与 student_zhang，实际 HTTP $STATUS，body=$BODY"
  fi
else
  fail "/me 跳过：上一步没有拿到 token"
fi

# 5) 不带 Token：401
request GET "$BASE_URL/api/auth/me"
if [ "$STATUS" = "401" ]; then pass "缺少 Authorization 返回 401"; else fail "缺少 Authorization 应为 401，实际 $STATUS"; fi

# 6) 篡改 Token 签名：401（验签失败）
if [ -n "$TOKEN" ]; then
  TAMPERED="${TOKEN%?}A"
  request GET "$BASE_URL/api/auth/me" "" "Authorization: Bearer $TAMPERED"
  if [ "$STATUS" = "401" ]; then pass "篡改签名后返回 401"; else fail "篡改签名应为 401，实际 $STATUS"; fi
fi

# 7) 管理员登录：200 + role ADMIN
request POST "$BASE_URL/api/auth/login" '{"username":"admin_li","password":"Admin@123"}'
ADMIN_TOKEN=$(json_field "$BODY" token)
if [ "$STATUS" = "200" ] && [ "$(json_field "$BODY" role)" = "ADMIN" ]; then
  pass "管理员登录成功，返回 role=ADMIN"
else
  fail "管理员登录失败：HTTP $STATUS，body=$BODY"
fi

# 8) 管理员 Token 访问 /me：仍为 ADMIN
if [ -n "$ADMIN_TOKEN" ]; then
  request GET "$BASE_URL/api/auth/me" "" "Authorization: Bearer $ADMIN_TOKEN"
  if [ "$STATUS" = "200" ] && [ "$(json_field "$BODY" role)" = "ADMIN" ]; then
    pass "管理员 /me 验签通过，role=ADMIN"
  else
    fail "管理员 /me 失败：HTTP $STATUS，body=$BODY"
  fi
fi

# 9) 数据库里是 BCrypt 哈希（$2a$ 前缀、长度 60），不含 @ 这类明文特征
HASH_1=$(docker compose exec -T user-db sh -c \
  'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" user_db -N -e "SELECT CONCAT(LEFT(password_hash,4),\":\",CHAR_LENGTH(password_hash)) FROM users WHERE id=1;"' 2>/dev/null | tr -d '\r')
if [ "$HASH_1" = '$2a$:60' ]; then pass "student_zhang 存的是 BCrypt 哈希（\$2a\$，长度 60）"; else fail "密码哈希格式异常：$HASH_1"; fi

HASH_2=$(docker compose exec -T user-db sh -c \
  'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" user_db -N -e "SELECT CONCAT(LEFT(password_hash,4),\":\",CHAR_LENGTH(password_hash)) FROM users WHERE id=2;"' 2>/dev/null | tr -d '\r')
if [ "$HASH_2" = '$2a$:60' ]; then pass "admin_li 存的是 BCrypt 哈希（\$2a\$，长度 60）"; else fail "密码哈希格式异常：$HASH_2"; fi

PLAINTEXT=$(docker compose exec -T user-db sh -c \
  'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" user_db -N -e "SELECT COUNT(*) FROM users WHERE password_hash LIKE CONCAT(\"%\",CHAR(64),\"%\");"' 2>/dev/null | tr -d '\r')
if [ "$PLAINTEXT" = "0" ]; then pass "密码列不含明文特征（@）"; else fail "密码列疑似含明文（count=$PLAINTEXT）"; fi

# 10) 经网关登录（统一入口可用）
request POST "$GATEWAY_URL/api/auth/login" '{"username":"student_zhang","password":"Student@123"}'
if [ "$STATUS" = "200" ] && [ -n "$(json_field "$BODY" token)" ]; then
  pass "经网关 8080 登录成功"
else
  fail "经网关登录失败：HTTP $STATUS，body=$BODY"
fi

# 11) 未登录访问受保护接口（经网关）→ 401
request GET "$GATEWAY_URL/api/users/current"
if [ "$STATUS" = "401" ]; then
  pass "未登录访问受保护接口返回 401"
else
  fail "未登录应 401，实际 $STATUS"
fi

# 12) 登录后携带 Token 访问受保护接口 → 200
request GET "$GATEWAY_URL/api/users/current" "" "Authorization: Bearer $TOKEN"
if [ "$STATUS" = "200" ] && [ "$(json_field "$BODY" username)" = "student_zhang" ]; then
  pass "登录后携带 Token 访问受保护接口返回 200"
else
  fail "带 Token 访问应 200，实际 $STATUS，body=$BODY"
fi

# 13) 低权限（USER）访问管理接口 → 403（越权被拒绝）
request GET "$GATEWAY_URL/api/admin/audits" "" "Authorization: Bearer $TOKEN"
if [ "$STATUS" = "403" ]; then
  pass "USER 访问管理接口被拒绝（403）"
else
  fail "USER 访问管理接口应 403，实际 $STATUS"
fi

# 14) 管理员访问管理接口 → 200（授权通过）
request GET "$GATEWAY_URL/api/admin/audits" "" "Authorization: Bearer $ADMIN_TOKEN"
if [ "$STATUS" = "200" ]; then
  pass "ADMIN 访问管理接口返回 200"
else
  fail "ADMIN 访问管理接口应 200，实际 $STATUS"
fi

# 15) 绕过网关直连后端 + 伪造身份头 → 401（签名校验挡住）
request GET "$USER_URL/api/users/current" "" \
  "X-User-Id: 2" "X-User-Role: ADMIN" "X-Gateway-Signature: forged"
if [ "$STATUS" = "401" ]; then
  pass "绕过网关伪造身份头被拒绝（401）"
else
  fail "伪造身份头应 401，实际 $STATUS"
fi

echo ""
echo "结果：PASS=$PASS  FAIL=$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
