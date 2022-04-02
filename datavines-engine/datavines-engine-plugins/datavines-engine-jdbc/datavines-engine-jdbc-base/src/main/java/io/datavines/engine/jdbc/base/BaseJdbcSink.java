package io.datavines.engine.jdbc.base;

import io.datavines.common.config.CheckResult;
import io.datavines.common.config.Config;
import io.datavines.common.config.enums.SinkType;
import io.datavines.common.utils.placeholder.PlaceholderUtils;
import io.datavines.engine.api.env.RuntimeEnvironment;
import io.datavines.engine.jdbc.api.JdbcRuntimeEnvironment;
import io.datavines.engine.jdbc.api.JdbcSink;
import io.datavines.engine.jdbc.api.entity.ResultList;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

public abstract class BaseJdbcSink implements JdbcSink {

    private static final Logger logger = LoggerFactory.getLogger(BaseJdbcSink.class);

    private Config config = new Config();

    @Override
    public void setConfig(Config config) {
        if(config != null) {
            this.config = config;
        }
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public CheckResult checkConfig() {
        List<String> requiredOptions = Arrays.asList("url", "dbtable", "user", "password","sql");

        List<String> nonExistsOptions = new ArrayList<>();
        requiredOptions.forEach(x->{
            if(!config.has(x)){
                nonExistsOptions.add(x);
            }
        });

        if (!nonExistsOptions.isEmpty()) {
            return new CheckResult(
                    false,
                    "please specify " + nonExistsOptions.stream().map(option ->
                            "[" + option + "]").collect(Collectors.joining(",")) + " as non-empty string");
        } else {
            return new CheckResult(true, "");
        }
    }

    @Override
    public void prepare(RuntimeEnvironment env) {

    }

    @Override
    public void output(List<ResultList> resultList, JdbcRuntimeEnvironment env) {

        if(env.getMetadataConnection() == null) {
            env.setMetadataConnection(getConnection());
        }

        Map<String,String> inputParameter = new HashMap<>();
        if (CollectionUtils.isNotEmpty(resultList)) {
            resultList.forEach(item -> {
                if(item != null) {
                    item.getResultList().forEach(x -> {
                        x.forEach((k,v) -> {
                            inputParameter.put(k, String.valueOf(v));
                        });
                    });
                }
            });
        }

        try {
            switch (SinkType.of(config.getString("plugin_type"))){
                case ACTUAL_VALUE:
                case TASK_RESULT:
                    String sql = config.getString("sql");
                    sql = PlaceholderUtils.replacePlaceholders(sql, inputParameter,true);
                    executeInsert(sql, env);
                    break;
                default:
                    break;
            }
        } catch (SQLException e){
            logger.error("sink error : {}", e.getMessage());
        }

    }

    private void executeInsert(String sql, JdbcRuntimeEnvironment env) throws SQLException {
        Statement statement =  env.getMetadataConnection().createStatement();
        statement.execute(sql);
        statement.close();
    }

    public Connection getConnection() {
        String className = getDriver();
        String url = config.getString("url");
        String username = config.getString("user");
        String password = config.getString("password");
        try {
            Class.forName(className);
        } catch (ClassNotFoundException exception) {
            logger.error("load driver error: " + exception.getLocalizedMessage());
        }

        Connection connection;
        try {
            connection = DriverManager.getConnection(url, username, password);
        } catch (SQLException exception) {
            logger.error("get connection error: " + exception.getLocalizedMessage());
            return null;
        }

        return connection;

    }

    protected abstract String getDriver();
}