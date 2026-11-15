#!/usr/bin/env bash
#
# 第八周：跨服务业务流程的端到端验证脚本
#
# 验证「发布失物前校验发布者」整条链路：
#   正常发布 → 200 且落库 +1
#   用户不存在 → 400 且不落库
#   依赖服务全部实例下线 → 503 且不落库
#   恢复后再次发布 → 200 且落库 +1
#
# 任一步不符合预期：该步记 FAIL，脚本最后以非 0 退出。
#
# 用法（在仓库根目录，Git Bash / macOS / Linux）：
#   bash scripts/e2e-test.sh
#
# 可用环境变量覆盖地址：LOST_URL、USER_URL、ADMIN_URL、NACOS_URL

set -u
set -o pipefail

LOST_URL="${LOST_URL:-http://localhost:8082}"
USER_URL="${USER_URL:-http://localhost:8081}"
ADMIN_URL="${ADMIN_URL:-http://localhost:8083}"
NACOS_URL="${NACOS_URL:-http://localhost:18080}"

PASS=0
FAIL=0

ok()  { PASS=$((PASS + 1)); echo "  [PASS] $*"; }
bad() { FAIL=$((FAIL + 1)); echo "  [FAIL] $*"; }

# 打印 HTTP 状态码并返回响应体
http_code() { curl -s -o /dev/null -w "%{http_code}" "$@"; }

# 统计 lost_items 行数（走 compose 里的 lost-db 容器，凭据由容器环境变量提供）
count_rows() {
  docker compose exec -T lost-db \
    sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -N lost_db -e "SELECT COUNT(*) FROM lost_items;"' \
    2>/dev/null | tr -d '\r' | tail -n1
}

# 发布一条失物，输出：第一行响应体，最后一行状态码
# 注意：请求体保持纯 ASCII——Git Bash 会把非 ASCII 的命令行参数转成本地编码（中文 Windows 为 GBK），
# 服务端会按 UTF-8 解析失败并返回 400，看上去像业务错误，其实是编码问题。
post_item() {
  local user_id="$1"
  curl -s -w "\n%{http_code}" \
    -H 'Content-Type: application/json' \
    -d "{\"itemName\":\"e2e-item-$(date +%s)-$RANDOM\",\"feature\":\"e2e-feature\",\"userId\":${user_id}}" \
    "$LOST_URL/api/lost-items"
}

# 把依赖服务恢复起来，避免脚本异常退出后环境残废
restore_user_service() {
  docker compose start user-service user-service-b >/dev/null 2>&1 || true
}
trap restore_user_service EXIT INT TERM

if [ ! -f compose.yaml ]; then
  echo "请在仓库根目录执行：bash scripts/e2e-test.sh" >&2
  exit 2
fi

echo "============================================================"
echo " 跨服务业务流程端到端验证：发布失物前校验发布者"
echo "============================================================"

# ------------------------------------------------------------------
echo "1. 环境检查（Nacos 控制台 + 三个服务健康接口）"
# ------------------------------------------------------------------
if [ "$(http_code "$NACOS_URL")" = "200" ]; then
  ok "Nacos 控制台可访问 ($NACOS_URL)"
else
  # 3.x 控制台根路径会 302 跳转，跟随跳转后再判断是否真的是控制台页面
  console_body="$(curl -fsSL --max-time 8 "$NACOS_URL/" 2>/dev/null)" || console_body=""
  if [ -n "$console_body" ] && printf '%s' "$console_body" | grep -qi '<title>' \
      && printf '%s' "$console_body" | grep -qi 'nacos'; then
    ok "Nacos 控制台可访问 ($NACOS_URL)"
  else
    bad "Nacos 控制台不可访问 ($NACOS_URL)"
  fi
fi

for entry in "user-service:$USER_URL" "lost-service:$LOST_URL" "admin-service:$ADMIN_URL"; do
  name="${entry%%:*}"
  url="${entry#*:}"
  if [ "$(http_code "$url/actuator/health")" = "200" ]; then
    ok "$name 健康接口正常 ($url)"
  else
    bad "$name 健康接口异常 ($url)"
  fi
done

# ------------------------------------------------------------------
echo "2. 正常发布：应 200，且 lost_items 行数 +1"
# ------------------------------------------------------------------
baseline="$(count_rows)"
echo "  发布前 lost_items 行数: ${baseline:-未知}"

response="$(post_item 1)"
body="$(printf '%s' "$response" | sed '$d')"
code="$(printf '%s' "$response" | tail -n1)"

if [ "$code" = "200" ]; then
  ok "发布成功 (HTTP 200): $body"
else
  bad "发布应返回 200，实际 $code: $body"
fi

after_insert="$(count_rows)"
if [ "${baseline:-x}" != "x" ] && [ "${after_insert:-x}" = "$((baseline + 1))" ]; then
  ok "行数 +1（$baseline → $after_insert）"
else
  bad "行数应 +1，实际 $baseline → ${after_insert:-未知}"
fi

# ------------------------------------------------------------------
echo "3. 用户不存在：应 400，且不落库"
# ------------------------------------------------------------------
before_reject="$(count_rows)"
response="$(post_item 9999)"
body="$(printf '%s' "$response" | sed '$d')"
code="$(printf '%s' "$response" | tail -n1)"

if [ "$code" = "400" ]; then
  ok "业务拒绝 (HTTP 400): $body"
else
  bad "用户不存在应返回 400，实际 $code: $body"
fi

after_reject="$(count_rows)"
if [ "${before_reject:-x}" = "${after_reject:-x}" ]; then
  ok "未写库（行数保持 $before_reject）"
else
  bad "校验失败却不落库，实际 $before_reject → ${after_reject:-未知}"
fi

# ------------------------------------------------------------------
echo "4. 模拟依赖不可用：停掉 user-service 全部实例，应 503 且不落库"
# ------------------------------------------------------------------
echo "  停止 user-service / user-service-b ..."
docker compose stop user-service user-service-b >/dev/null 2>&1
sleep 4

before_down="$(count_rows)"
response="$(post_item 1)"
body="$(printf '%s' "$response" | sed '$d')"
code="$(printf '%s' "$response" | tail -n1)"

if [ "$code" = "503" ]; then
  ok "依赖不可用 (HTTP 503): $body"
else
  bad "依赖不可用应返回 503，实际 $code: $body"
fi

if printf '%s' "$body" | grep -q "依赖服务不可用"; then
  ok "错误信息可读，未把调用失败当成业务成功"
else
  bad "503 响应体缺少可读错误信息: $body"
fi

after_down="$(count_rows)"
if [ "${before_down:-x}" = "${after_down:-x}" ]; then
  ok "失败期间未写库（行数保持 $before_down）"
else
  bad "失败期间不应写库，实际 $before_down → ${after_down:-未知}"
fi

# ------------------------------------------------------------------
echo "5. 恢复依赖后：应自动恢复 200，且行数再 +1"
# ------------------------------------------------------------------
restore_user_service
echo "  已启动 user-service / user-service-b，等待可调用（最多 60 秒）..."

recovered="no"
for _ in $(seq 1 30); do
  sleep 2
  response="$(post_item 1)"
  body="$(printf '%s' "$response" | sed '$d')"
  code="$(printf '%s' "$response" | tail -n1)"
  if [ "$code" = "200" ]; then
    recovered="yes"
    break
  fi
done

if [ "$recovered" = "yes" ]; then
  ok "恢复成功 (HTTP 200): $body"
else
  bad "恢复后仍不可调用，最后返回 $code: ${body:-无}"
fi

after_recover="$(count_rows)"
if [ "${after_down:-x}" != "x" ] && [ "${after_recover:-x}" = "$((after_down + 1))" ]; then
  ok "恢复后行数 +1（$after_down → $after_recover）"
else
  bad "恢复后行数应 +1，实际 $after_down → ${after_recover:-未知}"
fi

# ------------------------------------------------------------------
echo "============================================================"
echo " 结果：PASS=$PASS  FAIL=$FAIL"
echo "============================================================"

if [ "$FAIL" -gt 0 ]; then
  echo "端到端验证未通过" >&2
  exit 1
fi

echo "端到端验证全部通过"
exit 0
