package org.ninestick.codekin.gateway;

import io.netty.handler.ipfilter.IpFilterRuleType;
import io.netty.handler.ipfilter.IpSubnetFilterRule;
import io.netty.util.NetUtil;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;

/**
 * 仅从 TCP 对端或已配置的可信代理链确定客户端 IP，不使用未经验证的转发头。
 *
 * @author ninestick
 */
public class GatewayClientIpResolver {
    /** 代理常用的客户端地址链请求头。 */
    public static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    /** RFC 7239 代理请求头不参与本服务的地址判断。 */
    public static final String FORWARDED_HEADER = "Forwarded";
    /** 不参与 IP 判定，转发给业务服务前一并丢弃。 */
    public static final String REAL_IP_HEADER = "X-Real-IP";
    /** 防止上游或客户端提交过长的代理链。 */
    private static final int MAX_FORWARDED_LENGTH = 2048;
    /** 限制单次请求可解析的代理跳数。 */
    private static final int MAX_HOPS = 32;

    /** Nacos 中选定的网关部署方式。 */
    private final GatewayClientIpProperties.Mode mode;
    /** 仅在代理模式使用的可信代理网段。 */
    private final List<IpSubnetFilterRule> trustedProxies;

    public GatewayClientIpResolver(GatewayClientIpProperties properties) {
        this.mode = properties.getMode();
        if (mode == null) {
            throw new IllegalArgumentException("网关客户端 IP 获取方式不能为空");
        }
        List<IpSubnetFilterRule> rules = new ArrayList<>();
        if (mode == GatewayClientIpProperties.Mode.TRUSTED_PROXY) {
            if (properties.getTrustedProxies() == null || properties.getTrustedProxies().isEmpty()) {
                throw new IllegalArgumentException("代理模式必须配置可信代理 IP 或 CIDR");
            }
            for (String entry : properties.getTrustedProxies()) {
                if (!StringUtils.hasText(entry)) {
                    throw new IllegalArgumentException("可信代理地址不能为空");
                }
                String[] cidr = entry.trim().split("/", -1);
                if (cidr.length > 2) {
                    throw new IllegalArgumentException("可信代理 CIDR 格式不正确");
                }
                byte[] bytes = NetUtil.createByteArrayFromIpAddressString(cidr[0]);
                if (bytes == null) {
                    throw new IllegalArgumentException("可信代理必须是 IP 字面量或 CIDR");
                }
                int prefix;
                try {
                    prefix = cidr.length == 1 ? bytes.length * Byte.SIZE : Integer.parseInt(cidr[1]);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("可信代理 CIDR 前缀必须是整数", exception);
                }
                if (prefix < 1 || prefix > bytes.length * Byte.SIZE) {
                    throw new IllegalArgumentException("可信代理 CIDR 前缀不能覆盖全部地址空间");
                }
                try {
                    rules.add(new IpSubnetFilterRule(InetAddress.getByAddress(bytes), prefix,
                            IpFilterRuleType.ACCEPT));
                } catch (UnknownHostException exception) {
                    throw new IllegalArgumentException("可信代理 IP 格式不正确", exception);
                }
            }
            if (rules.isEmpty()) {
                throw new IllegalArgumentException("代理模式必须配置可信代理 IP 或 CIDR");
            }
        }
        this.trustedProxies = List.copyOf(rules);
    }

    /** 从右向左跳过可信代理，取第一个不可信的 IP，避免客户端伪造左侧条目。 */
    public String resolve(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            throw new IllegalStateException("网关无法取得 TCP 连接源地址");
        }
        InetAddress peer = remote.getAddress();
        if (mode == GatewayClientIpProperties.Mode.DIRECT || !isTrusted(peer)) {
            return NetUtil.bytesToIpAddress(peer.getAddress());
        }
        List<String> values = request.getHeaders().get(FORWARDED_FOR_HEADER);
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException("可信代理未提供 X-Forwarded-For");
        }
        String chain = String.join(",", values);
        if (chain.length() > MAX_FORWARDED_LENGTH) {
            throw new IllegalStateException("X-Forwarded-For 超出允许长度");
        }
        String[] hops = chain.split(",", -1);
        if (hops.length > MAX_HOPS) {
            throw new IllegalStateException("X-Forwarded-For 超出允许跳数");
        }
        for (int index = hops.length - 1; index >= 0; index--) {
            byte[] bytes = NetUtil.createByteArrayFromIpAddressString(hops[index].trim());
            if (bytes == null) {
                throw new IllegalStateException("可信代理传入无效的客户端 IP");
            }
            try {
                InetAddress candidate = InetAddress.getByAddress(bytes);
                if (!isTrusted(candidate)) {
                    return NetUtil.bytesToIpAddress(bytes);
                }
            } catch (UnknownHostException exception) {
                throw new IllegalStateException("可信代理传入无效的客户端 IP", exception);
            }
        }
        throw new IllegalStateException("代理链中没有可确认的客户端 IP");
    }

    /** 判断某个 IP 是否属于 Nacos 明确配置的可信代理范围。 */
    private boolean isTrusted(InetAddress address) {
        InetSocketAddress candidate = new InetSocketAddress(address, 0);
        for (IpSubnetFilterRule rule : trustedProxies) {
            if (rule.matches(candidate)) {
                return true;
            }
        }
        return false;
    }
}
