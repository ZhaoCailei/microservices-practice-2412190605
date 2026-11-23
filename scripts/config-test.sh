#!/usr/bin/env bash
# 配置中心验证脚本：证明 Nacos 上的配置确实被服务读取，且改配置不重启服务即可生效。
# 用法：bash scripts/config-test.sh
# 退出码：0 = 全部通过；非 0 = 有失败项。
set -u

NACOS_API="${NACOS_API:-http://localhost:8848}"
GROUP="${NACOS_GROUP:-ZCL_GROUP}"
DEV_NS="${NACOS_DEV_NAMESPACE:-zcl-dev}"
TEST_NS="${NACOS_TEST_NAMESPACE:-zcl-test}"
USER_URL="${USER_URL:-http://localhost:8081}"
LOST_URL="${LOST_URL:-http://localhost:8082}"
PASS=0
FAIL=0

pass() { PASS=$((PASS + 1)); echo "[PASS] $1"; }
fail() { FAIL=$((FAIL + 1)); echo "[FAIL] $1"; }

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi
USERNAME="${NACOS_USERNAME:?请在 .env 里设置 NACOS_USERNAME}"
PASSWORD="${NACOS_PASSWORD:?请在 .env 里设置 NACOS_PASSWORD}"

TOKEN=$(curl -s -X POST "$NACOS_API/nacos/v1/auth/login" \
  --data-urlencode "username=$USERNAME" \
  --data-urlencode "password=$PASSWORD" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
if [ -z "$TOKEN" ]; then
  echo "[FAIL] Nacos 登录失败，检查 .env"
  exit 1
fi

get_config() { # $1=namespaceId $2=dataId → 输出 JSON 响应
  curl -s "$NACOS_API/nacos/v3/admin/cs/config?dataId=$2&groupName=$GROUP&namespaceId=$1&accessToken=$TOKEN"
}

publish_config() { # $1=namespaceId $2=dataId $3=内容
  curl -s -X POST "$NACOS_API/nacos/v3/admin/cs/config?accessToken=$TOKEN" \
    --data-urlencode "dataId=$2" \
    --data-urlencode "groupName=$GROUP" \
    --data-urlencode "namespaceId=$1" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content=$3" >/dev/null
}

json_value() { # $1=json $2=字段（返回字符串值）
  echo "$1" | sed -n "s/.*\"$2\":\"\([^\"]*\)\".*/\1/p" | head -1
}

echo "=== 配置中心验证：$NACOS_API（dev=$DEV_NS / test=$TEST_NS）==="

# 1) .env 不入库
if git ls-files --error-unmatch .env >/dev/null 2>&1; then
  fail ".env 被 Git 跟踪了"
else
  pass ".env 未被 Git 跟踪（.gitignore 生效）"
fi

# 2) dev / test namespace 都存在
NS_LIST=$(curl -s "$NACOS_API/nacos/v3/admin/core/namespace/list?accessToken=$TOKEN")
if echo "$NS_LIST" | grep -q "\"namespace\":\"$DEV_NS\"" && echo "$NS_LIST" | grep -q "\"namespace\":\"$TEST_NS\""; then
  pass "dev/test 两个 namespace 已创建（$DEV_NS / $TEST_NS）"
else
  fail "namespace 缺失：$DEV_NS 或 $TEST_NS"
fi

# 3) 两个环境的同名 Data ID 存在，且值不同（环境边界清楚）
DEV_USER_JSON=$(get_config "$DEV_NS" user-service.yaml)
TEST_USER_JSON=$(get_config "$TEST_NS" user-service.yaml)
if echo "$DEV_USER_JSON" | grep -q 'expiration-seconds: 3600' && echo "$DEV_USER_JSON" | grep -q 'pool-size: 5'; then
  pass "dev user-service.yaml 的值正确（expiration=3600, pool=5）"
else
  fail "dev 配置内容异常：$DEV_USER_JSON"
fi
if echo "$TEST_USER_JSON" | grep -q 'expiration-seconds: 1800' && echo "$TEST_USER_JSON" | grep -q 'pool-size: 8'; then
  pass "test user-service.yaml 的值与 dev 不同（expiration=1800, pool=8）"
else
  fail "test 配置内容异常：$TEST_USER_JSON"
fi

# 4) 4 个服务在 dev/test 都有配置（集中管理）
MISSING=""
for data_id in user-service.yaml lost-service.yaml admin-service.yaml gateway-service.yaml; do
  for ns in "$DEV_NS" "$TEST_NS"; do
    get_config "$ns" "$data_id" | grep -q '"code":0' || MISSING="$MISSING $ns/$data_id"
  done
done
if [ -z "$MISSING" ]; then
  pass "4 个服务的 Data ID 在 dev/test 均存在（8 份配置）"
else
  fail "缺少配置：$MISSING"
fi

# 5) 服务实际读到了 Nacos 的值
USER_CONFIG=$(curl -s "$USER_URL/api/users/config")
if echo "$USER_CONFIG" | grep -q '"jwtExpirationSeconds":3600' && echo "$USER_CONFIG" | grep -q '"poolSize":5'; then
  pass "user-service 读到 dev 配置：jwt=3600, pool=5"
else
  fail "user-service 未读到预期配置：$USER_CONFIG"
fi
LOST_CONFIG=$(curl -s "$LOST_URL/api/lost-items/config")
if echo "$LOST_CONFIG" | grep -q '"feignReadTimeout":3000' && echo "$LOST_CONFIG" | grep -q '"poolSize":3'; then
  pass "lost-service 读到 dev 配置：feignRead=3000, pool=3"
else
  fail "lost-service 未读到预期配置：$LOST_CONFIG"
fi

# 6) 动态刷新：改配置 → 不重启生效
BEFORE_START=$(docker inspect -f '{{.State.StartedAt}}' microservices-practice-2412190605-user-service-1 2>/dev/null)
echo "--- 动态修改 dev 配置：jwt=7200, pool=6, log=DEBUG ---"
NEW_CONTENT='app:
  jwt:
    expiration-seconds: 7200
  demo:
    pool-size: 6
logging:
  level:
    com.zjgsu.zcl.user: DEBUG
'
publish_config "$DEV_NS" user-service.yaml "$NEW_CONTENT"
CHANGED=""
for i in $(seq 1 30); do
  sleep 1
  NEW_JSON=$(curl -s "$USER_URL/api/users/config")
  if echo "$NEW_JSON" | grep -q '"jwtExpirationSeconds":7200' && echo "$NEW_JSON" | grep -q '"poolSize":6'; then
    CHANGED="$i"
    break
  fi
done
if [ -n "$CHANGED" ]; then
  pass "配置变更后 ${CHANGED}s 内服务生效（jwt=7200, pool=6），未重启"
else
  fail "等了 30s 配置仍未生效：$(curl -s "$USER_URL/api/users/config")"
fi
AFTER_START=$(docker inspect -f '{{.State.StartedAt}}' microservices-practice-2412190605-user-service-1 2>/dev/null)
if [ "$BEFORE_START" = "$AFTER_START" ]; then
  pass "容器启动时间未变（$BEFORE_START），确认是热刷新而非重启"
else
  fail "容器发生了重启：before=$BEFORE_START after=$AFTER_START"
fi

# 7) 日志级别也随配置刷新
if echo "$NEW_JSON" | grep -q '"logLevel":"DEBUG"'; then
  pass "日志级别随配置刷新为 DEBUG"
else
  fail "日志级别未刷新：$NEW_JSON"
fi

# 8) 还原 dev 配置并确认还原生效
echo "--- 还原 dev 配置 ---"
RESTORE='app:
  jwt:
    expiration-seconds: 3600
  demo:
    pool-size: 5
logging:
  level:
    com.zjgsu.zcl.user: INFO
'
publish_config "$DEV_NS" user-service.yaml "$RESTORE"
RESTORED=""
for i in $(seq 1 30); do
  sleep 1
  BACK_JSON=$(curl -s "$USER_URL/api/users/config")
  if echo "$BACK_JSON" | grep -q '"jwtExpirationSeconds":3600' && echo "$BACK_JSON" | grep -q '"poolSize":5'; then
    RESTORED="$i"
    break
  fi
done
if [ -n "$RESTORED" ]; then
  pass "还原配置后 ${RESTORED}s 内回到 jwt=3600, pool=5"
else
  fail "配置未能还原：$(curl -s "$USER_URL/api/users/config")"
fi

# 9) 多环境切换：切到 test namespace（值不同）再切回 dev
echo "--- 切换到 test 环境（NACOS_CONFIG_NAMESPACE=$TEST_NS）---"
NACOS_CONFIG_NAMESPACE="$TEST_NS" docker compose up -d user-service user-service-b >/dev/null 2>&1
SWITCHED=""
for i in $(seq 1 60); do
  sleep 1
  T_JSON=$(curl -s "$USER_URL/api/users/config")
  if echo "$T_JSON" | grep -q '"jwtExpirationSeconds":1800' && echo "$T_JSON" | grep -q '"poolSize":8'; then
    SWITCHED="$i"
    break
  fi
done
if [ -n "$SWITCHED" ]; then
  pass "切到 test namespace 后 ${SWITCHED}s 内生效（jwt=1800, pool=8）"
else
  fail "切到 test 环境后仍未生效：$(curl -s "$USER_URL/api/users/config")"
fi

echo "--- 切回 dev 环境 ---"
NACOS_CONFIG_NAMESPACE="$DEV_NS" docker compose up -d user-service user-service-b >/dev/null 2>&1
SWITCH_BACK=""
for i in $(seq 1 60); do
  sleep 1
  B_JSON=$(curl -s "$USER_URL/api/users/config")
  if echo "$B_JSON" | grep -q '"jwtExpirationSeconds":3600' && echo "$B_JSON" | grep -q '"poolSize":5'; then
    SWITCH_BACK="$i"
    break
  fi
done
if [ -n "$SWITCH_BACK" ]; then
  pass "切回 dev namespace 后 ${SWITCH_BACK}s 内恢复（jwt=3600, pool=5）"
else
  fail "切回 dev 后仍未恢复：$(curl -s "$USER_URL/api/users/config")"
fi

# 10) 配置内容里没有密钥/密码
SECRET_HIT=""
for ns in "$DEV_NS" "$TEST_NS"; do
  for data_id in user-service.yaml lost-service.yaml admin-service.yaml gateway-service.yaml; do
    CONTENT=$(get_config "$ns" "$data_id")
    echo "$CONTENT" | grep -qiE 'password|JWT_SECRET|GATEWAY_SHARED_SECRET|secret' && SECRET_HIT="$SECRET_HIT $ns/$data_id"
  done
done
if [ -z "$SECRET_HIT" ]; then
  pass "Nacos 配置里没有密码/签名密钥等敏感项"
else
  fail "配置里疑似有敏感项：$SECRET_HIT"
fi

echo ""
echo "结果：PASS=$PASS  FAIL=$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
