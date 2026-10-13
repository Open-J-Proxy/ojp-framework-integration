import request from 'supertest';
import { getTestContext, closeTestContext } from './setup';
import { UserRepository } from '../src/repository/catalog/UserRepository';
import { ProductRepository } from '../src/repository/catalog/ProductRepository';
import { OrderRepository } from '../src/repository/checkout/OrderRepository';
import { OrderItemRepository } from '../src/repository/checkout/OrderItemRepository';
import { User } from '../src/entity/catalog/User';
import { Product } from '../src/entity/catalog/Product';
import { Express } from 'express';

describe('OrderAndOrderItemControllerIT', () => {
  let app: Express;
  let userRepository: UserRepository;
  let productRepository: ProductRepository;
  let orderRepository: OrderRepository;
  let orderItemRepository: OrderItemRepository;

  let user: User;
  let product1: Product;
  let product2: Product;

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
    await userRepository.deleteAll();
    await productRepository.deleteAll();

    const userObj = new User();
    userObj.username = 'bob';
    userObj.email = 'bob@example.com';
    user = await userRepository.save(userObj);

    const prod1 = new Product();
    prod1.name = 'Thing';
    prod1.price = 10.0;
    product1 = await productRepository.save(prod1);

    const prod2 = new Product();
    prod2.name = 'Gadget';
    prod2.price = 5.5;
    product2 = await productRepository.save(prod2);
  });

  test('createOrderWithItemsAndGetItems', async () => {
    const orderJson = {
      userId: user.id,
      orderItems: [
        { productId: product1.id, quantity: 2 },
        { productId: product2.id, quantity: 1 },
      ],
    };

    const postRes = await request(app)
      .post('/orders')
      .send(orderJson)
      .expect(200);

    expect(postRes.body.id).toBeDefined();
    const orderId = postRes.body.id;

    const getOrderRes = await request(app)
      .get(`/orders/${orderId}`)
      .expect(200);

    expect(getOrderRes.body.orderItems).toHaveLength(2);

    const getItemsRes = await request(app)
      .get(`/orders/${orderId}/items`)
      .expect(200);

    expect(getItemsRes.body).toHaveLength(2);
  });

  test('testDeleteOrder', async () => {
    const orderJson = {
      userId: user.id,
      orderItems: [
        { productId: product1.id, quantity: 2 },
        { productId: product2.id, quantity: 1 },
      ],
    };

    const postRes = await request(app)
      .post('/orders')
      .send(orderJson)
      .expect(200);

    expect(postRes.body.id).toBeDefined();
    const orderId = postRes.body.id;

    await request(app)
      .delete(`/orders/${orderId}`)
      .expect(204);

    await request(app)
      .get(`/orders/${orderId}`)
      .expect(404);
  });
});
