package com.example.shopservice.resource;

import com.example.shopservice.entity.Product;
import com.example.shopservice.entity.Review;
import com.example.shopservice.entity.User;
import com.example.shopservice.repository.ProductRepository;
import com.example.shopservice.repository.ReviewRepository;
import com.example.shopservice.repository.UserRepository;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/reviews")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReviewResource {

    @Inject
    private ReviewRepository reviewRepository;

    @Inject
    private UserRepository userRepository;

    @Inject
    private ProductRepository productRepository;

    @GET
    public List<Review> getAll() {
        return reviewRepository.findAll();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") Long id) {
        return reviewRepository.findById(id)
                .map(r -> Response.ok(r).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Transactional
    public Response create(Review review) {
        if (review.getUser() != null && review.getUser().getId() != null) {
            User user = userRepository.findById(review.getUser().getId()).orElse(null);
            if (user == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("User not found").build();
            }
            review.setUser(user);
        }
        if (review.getProduct() != null && review.getProduct().getId() != null) {
            Product product = productRepository.findById(review.getProduct().getId()).orElse(null);
            if (product == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Product not found").build();
            }
            review.setProduct(product);
        }
        Review saved = reviewRepository.save(review);
        return Response.status(Response.Status.CREATED).entity(saved).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") Long id, Review updated) {
        return reviewRepository.findById(id)
                .map(r -> {
                    if (updated.getUser() != null && updated.getUser().getId() != null) {
                        userRepository.findById(updated.getUser().getId()).ifPresent(r::setUser);
                    }
                    if (updated.getProduct() != null && updated.getProduct().getId() != null) {
                        productRepository.findById(updated.getProduct().getId()).ifPresent(r::setProduct);
                    }
                    r.setRating(updated.getRating());
                    r.setComment(updated.getComment());
                    return Response.ok(reviewRepository.save(r)).build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        return reviewRepository.deleteById(id)
                ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND).build();
    }
}
