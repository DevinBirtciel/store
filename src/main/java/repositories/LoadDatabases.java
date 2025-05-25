package repositories;

import entities.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class LoadDatabases {

    private static final Logger log = LoggerFactory.getLogger(LoadDatabases.class);

    @Bean
    public CommandLineRunner initProductDatabase(ProductRepository productRepository) {

        Product product = Product.builder().name("Soap").price(2.55f).id(1L).build();

        return args -> {
            log.info("Preloading " + productRepository.save(product));
        };
    }
}
