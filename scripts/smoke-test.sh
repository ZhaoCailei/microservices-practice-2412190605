#!/bin/bash
# 微服务冒烟测试：检查三个业务服务的健康接口
# 任一服务检查失败，脚本以非零状态退出（用于 CI / 作业自动检查）
set -u

FAILED=0

check_service() {
    name="$1"
    port="$2"
    echo "检查 ${name} (端口 ${port}) 健康状态..."
    if curl -s -f -o /dev/null --max-time 5 "http://localhost:${port}/actuator/health"; then
        echo "  [OK]   ${name} 正常"
    else
        echo "  [FAIL] ${name} 无法访问"
        FAILED=1
    fi
}

echo "开始微服务冒烟测试..."

check_service "user-service"  8081
check_service "lost-service"  8082
check_service "admin-service" 8083

if [ "$FAILED" -ne 0 ]; then
    echo "冒烟测试失败：存在不可用的服务"
    exit 1
fi

echo "冒烟测试通过：三个服务均正常"
exit 0
