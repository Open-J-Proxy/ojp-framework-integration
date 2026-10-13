import request from 'supertest';
import { getTestContext, closeTestContext } from './setup';
import { UserRepository } from '../src/repository/catalog/UserRepository';
import { ProductRepository } from '../src/repository/catalog/ProductRepository';
import { OrderRepository } from '../src/repository/checkout/OrderRepository';
import { OrderItemRepository } from '../src/repository/checkout/OrderItemRepository';
import { User } from '../src/entity/catalog/User';
import { Product } from '../src/entity/catalog/Product';
import { Order } from '../src/entity/checkout/Order';
import { Express } from 'express';

describe('OrderItemControllerIT', () => {
  let app: Express;
  let userRepository: UserRepository;
  let productRepository: ProductRepository;
  let orderRepository: OrderRepository;
  let orderItemRepository: OrderItemRepository;

  let user: User;
  let product: Product;
  let order: Order;

  beforeAll(async () => {
    const ctx = await getTestContext();
    app = ctx.app;
    userRepository = ctx.userRepository;
    productRepository = ctx.productRepository;
    orderRepository = ctx.orderRepository;
    orderItemRepository = ctx.orderItemRepository;
  });

  afterAll(async () => {
    await closeTestContext();
  });

  beforeEach(async () => {
    await orderItemRepository.deleteAll();
    await orderRepository.deleteAll();

    const userObj = new User();
    userObj.username = 'testuser';
    userObj.email = 'testuser@example.com';
    user = await userRepository.save(userObj);

    const prodObj = new Product();
    prodObj.name = 'TestProduct';
    prodObj.price = 25.99;
    product = await productRepository.save(prodObj);

    const orderObj = new Order();
    orderObj.userId = user.id;
    order = await orderRepository.save(orderObj);
  });

  test('testCreateOrderItem', async () => {
    const itemJson = {
      productId: product.id,
      quantity: 3,
    };

    const res = await request(app)
      .post(`/orders/${order.id}/items`)
      .send(itemJson)
      .expect(200);

    expect(res.body.id).toBeDefined();
    expect(res.body.quantity).toBe(3);
    expect(res.body.productId).toBe(product.id);
  });

  test('testGetOrderItems', async () => {
    const itemJson = {
      productId: product.id,
      quantity: 2,
    };

    await request(app)
      .post(`/orders/${order.id}/items`)
      .send(itemJson)
      .expect(200);

    const res = await request(app)
      .get(`/orders/${order.id}/items`)
      .expect(200);

    expect(res.body).toHaveLength(1);
    expect(res.body[0].quantity).toBe(2);
  });

  test('testGetOrderItem', async () => {
    const itemJson = {
      productId: product.id,
      quantity: 4,
    };

    const postRes = await request(app)
      .post(`/orders/${order.id}/items`)
      .send(itemJson)
      .expect(200);

    const orderItem = postRes.body;

    const res = await request(app)
      .get(`/orders/${order.id}/items/${orderItem.id}`)
      .expect(200);

    expect(res.body.id).toBe(orderItem.id);
    expect(res.body.quantity).toBe(4);
  });

  test('testUpdateOrderItem', async () => {
    const itemJson = {
      productId: product.id,
      quantity: 1,
    };

    const postRes = await request(app)
      .post(`/orders/${order.id}/items`)
      .send(itemJson)
      .expect(200);

    const orderItem = postRes.body;

    const updateJson = {
      productId: product.id,
      quantity: 5,
    };

    const res = await request(app)
      .put(`/orders/${order.id}/items/${orderItem.id}`)
      .send(updateJson)
      .expect(200);

    expect(res.body.quantity).toBe(5);
  });

  test('testDeleteOrderItem', async () => {
    const itemJson = {
      productId: product.id,
      quantity: 1,
    };

    const postRes = await request(app)
      .post(`/orders/${order.id}/items`)
      .send(itemJson)
      .expect(200);

    const orderItem = postRes.body;

    await request(app)
      .delete(`/orders/${order.id}/items/${orderItem.id}`)
      .expect(204);

    await request(app)
      .get(`/orders/${order.id}/items/${orderItem.id}`)
      .expect(404);
  });
});
