package com.baffalotech.integration.http.netty.protocol;

import java.util.Map;

import javax.net.ssl.SSLEngine;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;

import com.baffalotech.integration.configuration.IntegrationServerProperties;
import com.baffalotech.integration.core.StandardThreadExecutor;
import com.baffalotech.integration.http.netty.core.ProtocolsRegister;
import com.baffalotech.integration.http.netty.core.util.IOUtil;
import com.baffalotech.integration.http.netty.servlet.NettyServletContext;
import com.baffalotech.integration.http.netty.servlet.NettyServletFilterRegistration;
import com.baffalotech.integration.http.netty.servlet.NettyServletRegistration;
import com.baffalotech.integration.http.netty.servlet.handler.NettyServletChannelHandler;
import com.baffalotech.integration.http.netty.servlet.support.ServletEventListenerManager;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;

/**
 * httpServlet协议注册器
 * @author acer01
 *  2018/11/11/011
 */
public class HttpServletProtocolsRegister implements ProtocolsRegister {
    public static final int ORDER = 100;

    public static final String HANDLER_SSL = "SSL";
    public static final String HANDLER_AGGREGATOR = "Aggregator";
    public static final String HANDLER_SERVLET = "Servlet";
    public static final String HANDLER_HTTP_CODEC = "HttpCodec";

    /**
     * servlet上下文
     */
    private final NettyServletContext servletContext;
    /**
     * https 配置信息
     */
    private SslContext sslContext;
    private SslContextBuilder sslContextBuilder;
    private ChannelHandler servletHandler;

    public HttpServletProtocolsRegister(IntegrationServerProperties properties, NettyServletContext servletContext,StandardThreadExecutor serverExecutor, SslContextBuilder sslContextBuilder){
        this.servletContext = servletContext;
        this.servletHandler = new NettyServletChannelHandler(servletContext,properties,serverExecutor);
        this.sslContextBuilder = sslContextBuilder;
    }

    @Override
    public void onServerStart() throws Exception {
        ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();
        if(listenerManager.hasServletContextListener()){
            listenerManager.onServletContextInitialized(new ServletContextEvent(servletContext));
        }

        initFilter(servletContext);
        initServlet(servletContext);
    }

    @Override
    public void onServerStop() {
        ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();
        if(listenerManager.hasServletContextListener()){
            listenerManager.onServletContextDestroyed(new ServletContextEvent(servletContext));
        }

        destroyFilter();
        destroyServlet();
    }

    /**
     * 初始化过滤器
     * @param servletContext
     */
    protected void initFilter(NettyServletContext servletContext) throws ServletException {
        Map<String, NettyServletFilterRegistration> servletFilterRegistrationMap = servletContext.getFilterRegistrations();
        for(Map.Entry<String,NettyServletFilterRegistration> entry : servletFilterRegistrationMap.entrySet()){
            NettyServletFilterRegistration registration = entry.getValue();
            registration.getFilter().init(registration.getFilterConfig());
            registration.setInitParameter("_init","true");
        }
    }

    /**
     * 初始化servlet
     * @param servletContext
     */
    protected void initServlet(NettyServletContext servletContext) throws ServletException {
        Map<String, NettyServletRegistration> servletRegistrationMap = servletContext.getServletRegistrations();
        for(Map.Entry<String,NettyServletRegistration> entry : servletRegistrationMap.entrySet()){
            NettyServletRegistration registration = entry.getValue();
            registration.getServlet().init(registration.getServletConfig());
            registration.setInitParameter("_init","true");
        }
    }

    /**
     * 销毁过滤器
     */
    protected void destroyFilter(){
        Map<String, NettyServletFilterRegistration> servletRegistrationMap = servletContext.getFilterRegistrations();
        for(Map.Entry<String,NettyServletFilterRegistration> entry : servletRegistrationMap.entrySet()){
            NettyServletFilterRegistration registration = entry.getValue();
            Filter filter = registration.getFilter();
            if(filter == null) {
                continue;
            }
            String initFlag = registration.getInitParameter("_init");
            if(initFlag != null && "true".equals(initFlag)){
                filter.destroy();
            }
        }
    }

    /**
     * 销毁servlet
     */
    protected void destroyServlet(){
        Map<String, NettyServletRegistration> servletRegistrationMap = servletContext.getServletRegistrations();
        for(Map.Entry<String,NettyServletRegistration> entry : servletRegistrationMap.entrySet()){
            NettyServletRegistration registration = entry.getValue();
            Servlet servlet = registration.getServlet();
            if(servlet == null) {
                continue;
            }
            String initFlag = registration.getInitParameter("_init");
            if(initFlag != null && "true".equals(initFlag)){
                servlet.destroy();
            }
        }
    }

    @Override
    public boolean canSupport(ByteBuf msg) {
        int protocolEndIndex = IOUtil.indexOf(msg, HttpConstants.LF);
        if(protocolEndIndex < 9){
            return false;
        }

        if((char) msg.getByte(protocolEndIndex - 9) == 'H'
                && (char) msg.getByte(protocolEndIndex - 8) == 'T'
                && (char) msg.getByte(protocolEndIndex - 7) == 'T'
                &&  (char) msg.getByte(protocolEndIndex - 6) == 'P'){
            return true;
        }
        return false;
    }

    @Override
    public void register(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        //初始化SSL
        if (sslContextBuilder != null) {
            if(sslContext == null) {
                sslContext = sslContextBuilder.build();
            }
            SSLEngine engine = sslContext.newEngine(ch.alloc());
            pipeline.addLast(HANDLER_SSL, new SslHandler(engine,true));
        }

        //HTTP编码解码
        pipeline.addLast(HANDLER_HTTP_CODEC, new HttpServerCodec(4096, 8192, 5 * 1024 * 1024, false));

        //HTTP请求聚合，设置最大消息值为 5M
        pipeline.addLast(HANDLER_AGGREGATOR, new HttpObjectAggregator(5 * 1024 * 1024));

        //内容压缩
//        pipeline.addLast("ContentCompressor", new HttpContentCompressor());
//        pipeline.addLast("ContentDecompressor", new HttpContentDecompressor());

        //业务调度器, 让对应的Servlet处理请求
        pipeline.addLast(HANDLER_SERVLET, servletHandler);
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public String getProtocolName() {
        String name = "http";
        if(sslContextBuilder != null){
            name = name.concat("/https");
        }
        return name;
    }

    public NettyServletContext getServletContext() {
        return servletContext;
    }

    public SslContextBuilder getSslContextBuilder() {
        return sslContextBuilder;
    }
}
