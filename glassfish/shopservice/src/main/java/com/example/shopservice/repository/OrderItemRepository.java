package com.example.shopservice.repository;

import com.example.shopservice.entity.OrderItem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class OrderItemRepository {

    @PersistenceContext(unitName = "shopservice")
    private EntityManager em;

    public List<OrderItem> findByOrderId(Long orderId) {
        return em.createQuery(
                "SELECT i FROM OrderItem i WHERE i.order.id = :orderId", OrderItem.class)
                .setParameter("orderId", orderId)
                .getResultList();
    }

    public Optional<OrderItem> findByOrderIdAndItemId(Long orderId, Long itemId) {
        return em.createQuery(
                "SELECT i FROM OrderItem i WHERE i.order.id = :orderId AND i.id = :itemId",
                OrderItem.class)
                .setParameter("orderId", orderId)
                .setParameter("itemId", itemId)
                .getResultStream()
                .findFirst();
    }

    public OrderItem save(OrderItem item) {
        if (item.getId() == null) {
            em.persist(item);
            return item;
        }
        return em.merge(item);
    }

    public boolean delete(OrderItem item) {
        OrderItem managed = em.contains(item) ? item : em.merge(item);
        em.remove(managed);
        return true;
    }

    public List<OrderItem> saveAll(Iterable<OrderItem> items) {
        List<OrderItem> saved = new ArrayList<>();
        for (OrderItem item : items) {
            saved.add(save(item));
        }
        return saved;
    }

    public List<OrderItem> findAll() {
        return em.createQuery("SELECT i FROM OrderItem i", OrderItem.class).getResultList();
    }

    public void deleteAll() {
        em.createQuery("DELETE FROM OrderItem").executeUpdate();
    }
}
