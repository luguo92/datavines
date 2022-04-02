package io.datavines.server;

import com.zaxxer.hikari.HikariDataSource;
import io.datavines.common.utils.CommonPropertyUtils;
import io.datavines.common.utils.JSONUtils;
import io.datavines.common.utils.Stopper;
import io.datavines.common.utils.ThreadUtils;
import io.datavines.registry.api.Registry;
import io.datavines.server.coordinator.registry.Register;
import io.datavines.server.coordinator.server.cache.TaskExecuteManager;
import io.datavines.server.coordinator.server.failover.TaskFailover;
import io.datavines.server.coordinator.server.runner.JobScheduler;
import io.datavines.server.utils.SpringApplicationContext;
import io.datavines.spi.PluginLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Properties;

@SpringBootApplication
public class DataVinesServer {

    private static final Logger logger = LoggerFactory.getLogger(DataVinesServer.class);

    @Autowired
    private SpringApplicationContext springApplicationContext;

    @Resource
    private Environment environment;

    private Register register;

    private TaskExecuteManager taskExecuteManager;

    private TaskFailover taskFailover;

    public static void main(String[] args) {
        Thread.currentThread().setName(DataVinesConstants.THREAD_NAME_COORDINATOR_SERVER);
        SpringApplication.run(DataVinesServer.class);
    }

    @PostConstruct
    private void initializeAndStart() throws Exception {
        logger.info("datavines server start");

        initCommonProperties();

        taskExecuteManager = new TaskExecuteManager();
        taskExecuteManager.start();

        taskFailover = new TaskFailover(taskExecuteManager);

        Registry registry = PluginLoader
                .getPluginLoader(Registry.class)
                .getOrCreatePlugin(CommonPropertyUtils
                        .getString(CommonPropertyUtils.REGISTRY_TYPE, CommonPropertyUtils.REGISTRY_TYPE_DEFAULT));
        registry.init(CommonPropertyUtils.getProperties());

        register = new Register(registry, taskFailover);
        register.start();

        //start job scheduler
        JobScheduler jobScheduler = new JobScheduler(taskExecuteManager, register);
        jobScheduler.start();

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                close("shutdownHook");
            }
        }));
    }

    /**
     * gracefully close
     * @param cause close cause
     */
    private void close(String cause) {

        try {
            //execute only once
            if(Stopper.isStopped()){
                return;
            }

            logger.info("coordinator server is stopping ..., cause : {}", cause);

            // set stop signal is true
            Stopper.stop();

            ThreadUtils.sleep(2000);

            this.taskExecuteManager.close();

        } catch (Exception e) {
            logger.error("coordinator server stop exception ", e);
            System.exit(-1);
        }
    }

    private void initCommonProperties(){
        javax.sql.DataSource defaultDataSource =
                SpringApplicationContext.getBean(javax.sql.DataSource.class);
        HikariDataSource hikariDataSource = (HikariDataSource)defaultDataSource;
        CommonPropertyUtils.setValue("url",hikariDataSource.getJdbcUrl());
        CommonPropertyUtils.setValue("username", hikariDataSource.getUsername());
        CommonPropertyUtils.setValue("password", hikariDataSource.getPassword());
        CommonPropertyUtils.setValue("server.port", environment.getProperty("server.port"));
    }

}