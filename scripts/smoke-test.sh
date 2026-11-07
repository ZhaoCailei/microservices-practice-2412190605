#!/usr/bin/env bash
# 第七周冒烟测试：检查 Nacos 控制台（映射到宿主机的控制台端口）与三个业务服务的健康接口。
# 用法（在仓库根目录执行）：bash scripts/smoke-test.sh
# 任一检查失败，脚本以非零状态退出；全部通过才返回 0。脚本不输出任何密码或 Token。

set -u

# Nacos 控制台的实际映射地址：容器 8080 -> 宿主机 18080（不是旧版 8848/nacos 控制台路径）
CONSOLE_URL="${NACOS_CONSOLE_URL:-http://localhost:18080}"

# 业务服务名:端口（宿主发布端口）
SERVICES="user-service:8081 lost-service:8082 admin-service:8083"

FAILED=0
TOTAL=0

pass() {
    TOTAL=$((TOTAL + 1))
    printf '  [PASS] %s\n' "$1"
}

fail() {
    TOTAL=$((TOTAL + 1))
    FAILED=$((FAILED + 1))
    printf '  [FAIL] %s\n' "$1"
}

echo "开始第七周冒烟测试..."

# 1) Nacos 控制台（3.x 控制台根路径会 302 跳转到 /next/，需要跟随跳转）
#    真正的控制台是 HTML 页面（含 <title>）；旧版 8848/nacos 只返回一段纯文本提示，用此区分。
echo "1. 检查 Nacos 控制台：${CONSOLE_URL}"
console_body="$(curl -fsSL --max-time 8 "${CONSOLE_URL}/" 2>/dev/null)" || console_body=""
if [ -n "${console_body}" ] \
    && printf '%s' "${console_body}" | grep -qi '<title>' \
    && printf '%s' "${console_body}" | grep -qi 'nacos'; then
    pass "Nacos 控制台页面可访问（${CONSOLE_URL}）"
else
    fail "Nacos 控制台不可访问，或不是控制台页面（${CONSOLE_URL}）"
fi

# 2) 三个业务服务的健康接口
echo "2. 检查各服务健康接口 /actuator/health"
for entry in ${SERVICES}; do
    name="${entry%%:*}"
    port="${entry##*:}"
    health_body="$(curl -fsS --max-time 5 "http://localhost:${port}/actuator/health" 2>/dev/null)" || health_body=""
    if [ -n "${health_body}" ] && printf '%s' "${health_body}" | grep -q '"status":"UP"'; then
        pass "${name} (${port}) 健康接口正常"
    else
        fail "${name} (${port}) 健康接口不可用或状态非 UP"
    fi
done

# 3) 汇总
if [ "${FAILED}" -ne 0 ]; then
    echo "冒烟测试失败：${FAILED}/${TOTAL} 项检查未通过"
    exit 1
fi

echo "冒烟测试通过：${TOTAL} 项检查全部正常"
exit 0
