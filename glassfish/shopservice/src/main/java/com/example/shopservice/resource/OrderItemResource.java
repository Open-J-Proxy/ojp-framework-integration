package com.example.shopservice.resource;

import com.example.shopservice.entity.Order;
import com.example.shopservice.entity.OrderItem;
import com.example.shopservice.entity.Product;
import com.example.shopservice.repository.OrderItemRepository;
import com.example.shopservice.repository.OrderRepository;
import com.example.shopservice.repository.ProductRepository;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

@Path("/orders/{orderId}/items")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderItemResource {

    @Inject
    private OrderItemRepository orderItemRepository;

    @Inject
    private OrderRepository orderRepository;

    @Inject
    private ProductRepository productRepository;

    @GET
    public List<OrderItem> getAll(@PathParam("orderId") Long orderId) {
        return orderItemRepository.findByOrderId(orderId);
    }

    @GET
    @Path("/{itemId}")
    public Response get(@PathParam("orderId") Long orderId, @PathParam("itemId") Long itemId) {
        return orderItemRepository.findByOrderIdAndItemId(orderId, itemId)
                .map(i -> Response.ok(i).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Transactional
    public Response create(@PathParam("orderId") Long orderId, OrderItem item) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Order not found").build();
        }
        if (item.getProduct() != null && item.getProduct().getId() != null) {
            Product product = productRepository.findById(item.getProduct().getId()).orElse(null);
            if (product == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Product not found").build();
            }
            item.setProduct(product);
        }
        item.setOrder(order);
        OrderItem saved = orderItemRepository.save(item);
        return Response.status(Response.Status.CREATED).entity(saved).build();
    }

    @PUT
    @Path("/{itemId}")
    @Transactional
    public Response update(@PathParam("orderId") Long orderId,
                           @PathParam("itemId") Long itemId,
                           OrderItem updated) {
        return orderItemRepository.findByOrderIdAndItemId(orderId, itemId)
                .map(i -> {
                    if (updated.getProduct() != null && updated.getProduct().getId() != null) {
                        productRepository.findById(updated.getProduct().getId())
                                .ifPresent(i::setProduct);
                    }
                    i.setQuantity(updated.getQuantity());
                    return Response.ok(orderItemRepository.save(i)).build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{itemId}")
    @Transactional
    public Response delete(@PathParam("orderId") Long orderId, @PathParam("itemId") Long itemId) {
        return orderItemRepository.findByOrderIdAndItemId(orderId, itemId)
                .map(i -> {
                    orderItemRepository.delete(i);
                    return Response.noContent().build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }
}
