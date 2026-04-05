package com.example.shopservice.repository;

import com.example.shopservice.entity.Order;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class OrderRepository {

    @PersistenceContext(unitName = "shopservice")
    private EntityManager em;

    public List<Order> findAll() {
        return em.createQuery("SELECT o FROM Order o", Order.class).getResultList();
    }

    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(em.find(Order.class, id));
    }

    public Order save(Order order) {
        if (order.getId() == null) {
            em.persist(order);
            return order;
        }
        return em.merge(order);
    }

    public boolean deleteById(Long id) {
        Order order = em.find(Order.class, id);
        if (order == null) return false;
        em.remove(order);
        return true;
    }

    public List<Order> saveAll(Iterable<Order> orders) {
        List<Order> saved = new ArrayList<>();
        for (Order order : orders) {
            saved.add(save(order));
        }
        return saved;
    }

    public void deleteAll() {
        em.createQuery("DELETE FROM Order").executeUpdate();
    }
}
