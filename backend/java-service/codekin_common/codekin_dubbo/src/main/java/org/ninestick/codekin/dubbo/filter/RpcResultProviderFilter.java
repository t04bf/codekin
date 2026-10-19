package org.ninestick.codekin.dubbo.filter;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import org.ninestick.codekin.dubbo.support.RpcResultSupport;
import org.ninestick.codekin.model.common.RpcCode;

/**
 * 提供方统一异常出口，先于外层 Dubbo ExceptionFilter 的响应回调转换业务异常。
 *
 * @author ninestick
 */
@Activate(group = CommonConstants.PROVIDER, order = 10000)
public class RpcResultProviderFilter implements Filter {
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        return RpcResultSupport.invoke(invoker, invocation, RpcCode.INTERNAL_ERROR);
    }
}
