package com.example.shopservice.repository;

import com.example.shopservice.entity.Product;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ProductRepository {

    @PersistenceContext(unitName = "shopservice")
    private EntityManager em;

    public List<Product> findAll() {
        return em.createQuery("SELECT p FROM Product p", Product.class).getResultList();
    }

    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(em.find(Product.class, id));
    }

    public Product save(Product product) {
        if (product.getId() == null) {
            em.persist(product);
            return product;
        }
        return em.merge(product);
    }

    public boolean deleteById(Long id) {
        Product product = em.find(Product.class, id);
        if (product == null) return false;
        em.remove(product);
        return true;
    }

    public List<Product> saveAll(Iterable<Product> products) {
        List<Product> saved = new ArrayList<>();
        for (Product product : products) {
            saved.add(save(product));
        }
        em.flush();
        return saved;
    }

    public void deleteAll() {
        em.createQuery("DELETE FROM Product").executeUpdate();
    }
}
