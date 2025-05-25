package controllers;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import entities.Product;
import exceptions.ProductNotFoundException;
import repositories.ProductRepository;

@RestController
@RequestMapping("/store/v1")
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping("/products")
    public CollectionModel<EntityModel<Product>> all() {
        List<EntityModel<Product>> products = productRepository.findAll().stream()
                .map(product -> EntityModel.of(product,
                        linkTo(methodOn(ProductController.class).all()).withRel("products")))
                .collect(Collectors.toList());
        return CollectionModel.of(products, linkTo(methodOn(ProductController.class).all()).withSelfRel());
    }

    @PostMapping("/products")
    public Product newProduct(@Valid @RequestBody Product newProduct) {
        return productRepository.save(newProduct);
    }

    @GetMapping("/products/{productId}")
    public EntityModel<Product> one(@NotNull @PathVariable String productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return EntityModel.of(product,
                linkTo(methodOn(ProductController.class).one(productId)).withSelfRel(),
                linkTo(methodOn(ProductController.class).all()).withRel("products"));
    }

    @GetMapping("/products/name/{productName}")
    public EntityModel<Product> oneByName(@NotNull @PathVariable String productName) {
        Product product = productRepository.findByName(productName)
                .orElseThrow(() -> new ProductNotFoundException(productName));
        return EntityModel.of(product,
                linkTo(methodOn(ProductController.class).oneByName(productName)).withSelfRel(),
                linkTo(methodOn(ProductController.class).all()).withRel("products"));
    }

    @Transactional
    @PutMapping("/products/{productId}")
    public Product updateProduct(@NotNull @PathVariable String productId, @Valid @RequestBody Product newProduct) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (newProduct.getName() != null && !newProduct.getName().isBlank()) {
            product.setName(newProduct.getName());
        }
        if (newProduct.getPrice() == 0.0f) {
            product.setPrice(newProduct.getPrice());
        }

        return productRepository.save(product);
    }

    @Transactional
    @DeleteMapping("/products/{productId}")
    public void deleteProduct(@NotNull @PathVariable String productId) {
        productRepository.deleteById(productId);
    }

    @Transactional
    @DeleteMapping("/products/name/{productName}")
    public void deleteProductByName(@NotNull @PathVariable String productName) {
        // Check if the product exists before attempting to delete
        productRepository.findByName(productName)
                .orElseThrow(() -> new ProductNotFoundException(productName));
        // Get the product and delete it

       productRepository.deleteByName(productName);
    }
}