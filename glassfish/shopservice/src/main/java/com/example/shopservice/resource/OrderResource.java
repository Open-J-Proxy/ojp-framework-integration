package com.example.shopservice.resource;

import com.example.shopservice.entity.Order;
import com.example.shopservice.entity.User;
import com.example.shopservice.repository.OrderRepository;
import com.example.shopservice.repository.UserRepository;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.List;

@Path("/orders")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    @Inject
    private OrderRepository orderRepository;

    @Inject
    private UserRepository userRepository;

    @GET
    public List<Order> getAll() {
        return orderRepository.findAll();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") Long id) {
        return orderRepository.findById(id)
                .map(o -> Response.ok(o).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Transactional
    public Response create(Order order) {
        if (order.getUser() != null && order.getUser().getId() != null) {
            User user = userRepository.findById(order.getUser().getId()).orElse(null);
            if (user == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("User not found").build();
            }
            order.setUser(user);
        }
        order.setOrderDate(LocalDateTime.now());
        Order saved = orderRepository.save(order);
        return Response.status(Response.Status.CREATED).entity(saved).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") Long id, Order updated) {
        return orderRepository.findById(id)
                .map(o -> {
                    if (updated.getUser() != null && updated.getUser().getId() != null) {
                        userRepository.findById(updated.getUser().getId())
                                .ifPresent(o::setUser);
                    }
                    return Response.ok(orderRepository.save(o)).build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        return orderRepository.deleteById(id)
                ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND).build();
    }
}
