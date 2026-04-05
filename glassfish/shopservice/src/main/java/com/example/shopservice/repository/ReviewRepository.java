package com.example.shopservice.repository;

import com.example.shopservice.entity.Review;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ReviewRepository {

    @PersistenceContext(unitName = "shopservice")
    private EntityManager em;

    public List<Review> findAll() {
        return em.createQuery("SELECT r FROM Review r", Review.class).getResultList();
    }

    public Optional<Review> findById(Long id) {
        return Optional.ofNullable(em.find(Review.class, id));
    }

    public Review save(Review review) {
        if (review.getId() == null) {
            em.persist(review);
            return review;
        }
        return em.merge(review);
    }

    public boolean deleteById(Long id) {
        Review review = em.find(Review.class, id);
        if (review == null) return false;
        em.remove(review);
        return true;
    }
}
