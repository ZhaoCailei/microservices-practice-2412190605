#!/usr/bin/env bash
# 初始化 Nacos 配置中心：创建 dev/test 两个 namespace，并发布 4 个服务的非敏感配置。
# 可重复执行（已存在的 namespace 会跳过；配置会被覆盖为脚本里的值）。
# 用法：bash scripts/config-setup.sh
set -eu

NACOS_API="${NACOS_API:-http://localhost:8848}"
GROUP="${NACOS_GROUP:-ZCL_GROUP}"
DEV_NS="${NACOS_DEV_NAMESPACE:-zcl-dev}"
TEST_NS="${NACOS_TEST_NAMESPACE:-zcl-test}"

# 从 .env 读取 Nacos 账号（.env 不入库，脚本不写死密码）
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
  echo "[FAIL] Nacos 登录失败，检查 .env 里的 NACOS_USERNAME / NACOS_PASSWORD"
  exit 1
fi
echo "[OK] 已登录 Nacos：$NACOS_API"

ensure_namespace() { # $1=namespaceId $2=显示名 $3=描述
  local ns="$1" name="$2" desc="$3"
  local exists
  exists=$(curl -s "$NACOS_API/nacos/v3/admin/core/namespace/list?accessToken=$TOKEN" \
    | grep -o "\"namespace\":\"$ns\"" | head -1)
  if [ -n "$exists" ]; then
    echo "[OK] namespace $ns 已存在"
    return
  fi
  local resp
  resp=$(curl -s -X POST "$NACOS_API/nacos/v3/admin/core/namespace?accessToken=$TOKEN" \
    -d "namespaceId=$ns" --data-urlencode "namespaceName=$name" --data-urlencode "namespaceDesc=$desc")
  if echo "$resp" | grep -q '"code":0'; then
    echo "[OK] 创建 namespace $ns"
  else
    echo "[FAIL] 创建 namespace $ns 失败：$resp"
    exit 1
  fi
}

publish() { # $1=namespaceId $2=dataId $3=内容
  local ns="$1" data_id="$2" content="$3" resp
  resp=$(curl -s -X POST "$NACOS_API/nacos/v3/admin/cs/config?accessToken=$TOKEN" \
    --data-urlencode "dataId=$data_id" \
    --data-urlencode "groupName=$GROUP" \
    --data-urlencode "namespaceId=$ns" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content=$content")
  if echo "$resp" | grep -q '"code":0'; then
    echo "[OK] 发布 $ns / $GROUP / $data_id"
  else
    echo "[FAIL] 发布 $data_id 失败：$resp"
    exit 1
  fi
}

USER_DEV='app:
  jwt:
    expiration-seconds: 3600
  demo:
    pool-size: 5
logging:
  level:
    com.zjgsu.zcl.user: INFO
'
USER_TEST='app:
  jwt:
    expiration-seconds: 1800
  demo:
    pool-size: 8
logging:
  level:
    com.zjgsu.zcl.user: DEBUG
'
LOST_DEV='spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 3000
            read-timeout: 3000
app:
  demo:
    pool-size: 3
logging:
  level:
    com.zjgsu.zcl.lost: INFO
'
LOST_TEST='spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 2000
            read-timeout: 2000
app:
  demo:
    pool-size: 6
logging:
  level:
    com.zjgsu.zcl.lost: DEBUG
'
ADMIN_DEV='app:
  demo:
    audit-page-size: 20
logging:
  level:
    com.zjgsu.zcl.admin: INFO
'
ADMIN_TEST='app:
  demo:
    audit-page-size: 10
logging:
  level:
    com.zjgsu.zcl.admin: DEBUG
'
GATEWAY_DEV='logging:
  level:
    com.zjgsu.zcl.gateway: INFO
'
GATEWAY_TEST='logging:
  level:
    com.zjgsu.zcl.gateway: DEBUG
'

echo "=== 创建 namespace ==="
ensure_namespace "$DEV_NS" dev "开发环境"
ensure_namespace "$TEST_NS" test "测试环境"

echo "=== 发布开发环境配置（$DEV_NS）==="
publish "$DEV_NS" user-service.yaml "$USER_DEV"
publish "$DEV_NS" lost-service.yaml "$LOST_DEV"
publish "$DEV_NS" admin-service.yaml "$ADMIN_DEV"
publish "$DEV_NS" gateway-service.yaml "$GATEWAY_DEV"

echo "=== 发布测试环境配置（$TEST_NS）==="
publish "$TEST_NS" user-service.yaml "$USER_TEST"
publish "$TEST_NS" lost-service.yaml "$LOST_TEST"
publish "$TEST_NS" admin-service.yaml "$ADMIN_TEST"
publish "$TEST_NS" gateway-service.yaml "$GATEWAY_TEST"

echo ""
echo "完成：dev($DEV_NS) 与 test($TEST_NS) 各 4 个 Data ID，Group=$GROUP"
