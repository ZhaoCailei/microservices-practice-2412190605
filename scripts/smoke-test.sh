#!/bin/bash
echo "开始微服务冒烟测试..."

echo "1. 检查 User Service (8081) 健康状态..."
if curl -s -f -o /dev/null http://localhost:8081/actuator/health; then
    echo "✅ User Service 正常运行！"
else
    echo "❌ User Service 无法访问！"
fi

echo "2. 检查 Lost Service (8082) 健康状态..."
if curl -s -f -o /dev/null http://localhost:8082/actuator/health; then
    echo "✅ Lost Service 正常运行！"
else
    echo "❌ Lost Service 无法访问！"
fi
echo "冒烟测试结束。"