package com.example.atomikos.config;

import com.atomikos.icatch.jta.UserTransactionManager;
import jakarta.transaction.SystemException;
import jakarta.transaction.UserTransaction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.jta.JtaTransactionManager;

@Configuration
@EnableTransactionManagement
public class TransactionManagerConfig {

    @Bean(name = "atomikosTransactionManager", initMethod = "init", destroyMethod = "close")
    public UserTransactionManager atomikosTransactionManager() throws SystemException {
        UserTransactionManager utm = new UserTransactionManager();
        utm.setForceShutdown(true);
        utm.setTransactionTimeout(60);
        return utm;
    }

    @Bean(name = "atomikosUserTransaction")
    public UserTransaction atomikosUserTransaction() throws SystemException {
        com.atomikos.icatch.jta.UserTransactionImp userTransaction = new com.atomikos.icatch.jta.UserTransactionImp();
        userTransaction.setTransactionTimeout(60);
        return userTransaction;
    }

    @Bean(name = "transactionManager")
    @DependsOn({"atomikosTransactionManager", "atomikosUserTransaction"})
    public JtaTransactionManager transactionManager() throws SystemException {
        JtaTransactionManager jtaTransactionManager = new JtaTransactionManager();
        jtaTransactionManager.setTransactionManager(atomikosTransactionManager());
        jtaTransactionManager.setUserTransaction(atomikosUserTransaction());
        jtaTransactionManager.setAllowCustomIsolationLevels(true);
        return jtaTransactionManager;
    }
}
