#!/bin/sh
set -eu

# Nacos 3 首次启动没有管理员密码；已有密码只校验，不覆盖。
nacos_url=${NACOS_API_URL:-http://nacos:8848}
config_dir=${NACOS_CONFIG_DIR:-/config}
attempt=0
access_token=
while [ "$attempt" -lt 90 ]; do
    if login_response=$(curl --silent --show-error --max-time 3 --fail \
        --data-urlencode 'username=nacos' \
        --data-urlencode "password=$NACOS_ADMIN_PASSWORD" \
        "$nacos_url/nacos/v3/auth/user/login"); then
        access_token=$(printf '%s' "$login_response" | tr -d '\n' |
            sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
        if [ -n "$access_token" ]; then
            break
        fi
    fi

    curl --silent --show-error --max-time 3 --fail --output /dev/null \
        --data-urlencode "password=$NACOS_ADMIN_PASSWORD" \
        "$nacos_url/nacos/v3/auth/user/admin" || true
    attempt=$((attempt + 1))
    sleep 2
done

if [ -z "$access_token" ]; then
    echo 'Nacos 管理员初始化或现有密码校验失败，请检查持久化数据与统一密码。' >&2
    exit 1
fi

# 以仓库副本为准；重复启动时按相同 Data ID 更新 public/CODEKIN 配置。
found_config=false
for config_file in "$config_dir"/*.yml; do
    if [ ! -f "$config_file" ]; then
        continue
    fi
    found_config=true
    data_id=${config_file##*/}
    if ! publish_response=$(curl --silent --show-error --fail --max-time 15 \
        --retry 5 --retry-connrefused --retry-delay 2 \
        -H "accessToken: $access_token" \
        --data-urlencode 'namespaceId=public' \
        --data-urlencode 'groupName=CODEKIN' \
        --data-urlencode "dataId=$data_id" \
        --data-urlencode 'type=yaml' \
        --data-urlencode "content@$config_file" \
        "$nacos_url/nacos/v3/admin/cs/config"); then
        echo "Nacos 配置发布失败: $data_id" >&2
        exit 1
    fi
    if ! printf '%s' "$publish_response" | tr -d '\n' |
        grep -Eq '"code"[[:space:]]*:[[:space:]]*0([,}])'; then
        echo "Nacos 配置返回失败状态: $data_id" >&2
        exit 1
    fi
    echo "Nacos 配置已同步: $data_id"
done

if [ "$found_config" != true ]; then
    echo '未找到 Nacos YAML 配置文件，拒绝空导入。' >&2
    exit 1
fi
