package config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

@TestConfiguration
public class SqlInitConfig {

    @Bean
    public DataSourceInitializer checkoutInit(@Qualifier("checkoutDataSource") DataSource ds) {
        var pop = new ResourceDatabasePopulator();
        pop.setSqlScriptEncoding("UTF-8");
        pop.addScript(new ClassPathResource("schema-checkout.sql"));
        var init = new DataSourceInitializer();
        init.setDataSource(ds);
        init.setDatabasePopulator(pop);
        init.setEnabled(true);
        return init;
    }

    @Bean
    public DataSourceInitializer catalogInit(@Qualifier("catalogDataSource") DataSource ds) {
        var pop = new ResourceDatabasePopulator();
        pop.setSqlScriptEncoding("UTF-8");
        pop.addScript(new ClassPathResource("schema-catalog.sql"));
        var init = new DataSourceInitializer();
        init.setDataSource(ds);
        init.setDatabasePopulator(pop);
        init.setEnabled(true);
        return init;
    }
}
