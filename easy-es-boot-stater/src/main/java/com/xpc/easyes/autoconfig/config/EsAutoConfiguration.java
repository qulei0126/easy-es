package com.xpc.easyes.autoconfig.config;

import com.xpc.easyes.autoconfig.constants.PropertyKeyConstants;
import com.xpc.easyes.core.cache.GlobalConfigCache;
import com.xpc.easyes.core.config.GlobalConfig;
import com.xpc.easyes.core.enums.FieldStrategy;
import com.xpc.easyes.core.enums.IdType;
import com.xpc.easyes.core.toolkit.ExceptionUtils;
import com.xpc.easyes.core.toolkit.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.*;

import static com.xpc.easyes.core.constants.BaseEsConstants.COLON;
import static com.xpc.easyes.core.constants.BaseEsConstants.DEFAULT_SCHEMA;

/**
 * es自动配置
 *
 * @ProjectName: easy-es
 * @Package: com.xpc.easyes.core.config
 * @Description: 配置RestHighLevelClient
 * @Author: xpc
 * @Version: 1.0
 * <p>
 * Copyright © 2021 xpc1024 All Rights Reserved
 **/
@Configuration
@EnableConfigurationProperties(EsConfigProperties.class)
@ConditionalOnClass(RestHighLevelClient.class)
@ConditionalOnProperty(prefix = "easy-es", name = {"enable"}, havingValue = "true", matchIfMissing = true)
public class EsAutoConfiguration implements InitializingBean, EnvironmentAware, PropertyKeyConstants {
    @Autowired
    private EsConfigProperties esConfigProperties;
    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Bean
    @ConditionalOnMissingBean
    public RestHighLevelClient restHighLevelClient() {
        // 处理地址
        String address = environment.getProperty(ADDRESS);
        if (StringUtils.isEmpty(address)) {
            throw ExceptionUtils.eee("please config the es address");
        }
        if (!address.contains(COLON)) {
            throw ExceptionUtils.eee("the address must contains port and separate by ':'");
        }
        String schema = StringUtils.isEmpty(esConfigProperties.getSchema())
                ? DEFAULT_SCHEMA : esConfigProperties.getSchema();
        List<HttpHost> hostList = new ArrayList<>();
        Arrays.stream(esConfigProperties.getAddress().split(","))
                .forEach(item -> hostList.add(new HttpHost(item.split(":")[0],
                        Integer.parseInt(item.split(":")[1]), schema)));

        // 转换成 HttpHost 数组
        HttpHost[] httpHost = hostList.toArray(new HttpHost[]{});
        // 构建连接对象
        RestClientBuilder builder = RestClient.builder(httpHost);

        // 设置账号密码之类的
        String username = environment.getProperty(USERNAME);
        String password = environment.getProperty(PASSWORD);
        if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            // 设置账号密码
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(esConfigProperties.getUsername(), esConfigProperties.getPassword()));
            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                return httpClientBuilder;
            });
        }
        //TODO 其它设置,比如超时时间 异步之类的 后续优化

        return new RestHighLevelClient(builder);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        GlobalConfig globalConfig = new GlobalConfig();
        GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
        Optional.ofNullable(environment.getProperty(TABLE_PREFIX)).ifPresent(dbConfig::setTablePrefix);
        Optional.ofNullable(environment.getProperty(ID_TYPE))
                .ifPresent(i -> dbConfig.setIdType(Enum.valueOf(IdType.class, i.toUpperCase(Locale.ROOT))));
        Optional.ofNullable(environment.getProperty(FIELD_STRATEGY))
                .ifPresent(f -> dbConfig.setFieldStrategy(Enum.valueOf(FieldStrategy.class, f.toUpperCase(Locale.ROOT))));
        globalConfig.setDbConfig(dbConfig);
        GlobalConfigCache.setGlobalConfig(globalConfig);
    }
}