package org.ninestick.codekin.dubbo.filter;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import org.apache.dubbo.rpc.cluster.filter.ClusterFilter;
import org.ninestick.codekin.dubbo.support.RpcResultSupport;
import org.ninestick.codekin.model.common.RpcCode;

/**
 * 消费方在集群调用外层转换失败，覆盖无可用实例及最终超时，不在重试过程中吞掉异常。
 *
 * @author ninestick
 */
@Activate(group = CommonConstants.CONSUMER, order = -10000)
public class RpcResultConsumerFilter implements ClusterFilter {
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        return RpcResultSupport.invoke(invoker, invocation, RpcCode.CALL_FAILED);
    }
}
