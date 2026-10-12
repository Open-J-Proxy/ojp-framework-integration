import request from 'supertest';
import { getTestContext, closeTestContext } from './setup';
import { ProductRepository } from '../src/repository/catalog/ProductRepository';
import { ReviewRepository } from '../src/repository/catalog/ReviewRepository';
import { Express } from 'express';

describe('ProductControllerIT', () => {
  let app: Express;
  let productRepository: ProductRepository;
  let reviewRepository: ReviewRepository;

  beforeAll(async () => {
    const ctx = await getTestContext();
    app = ctx.app;
    productRepository = ctx.productRepository;
    reviewRepository = ctx.reviewRepository;
  });

  afterAll(async () => {
    await closeTestContext();
  });

  beforeEach(async () => {
    await reviewRepository.deleteAll();
    await productRepository.deleteAll();
  });

  test('testCreateAndGetProduct', async () => {
    const json = { name: 'Widget', price: 19.99 };

    const postRes = await request(app)
      .post('/products')
      .send(json)
      .expect(200);

    expect(postRes.body.id).toBeDefined();
    expect(postRes.body.name).toBe('Widget');

    const products = await productRepository.findAll();
    const prod = products[0];

    const getRes = await request(app)
      .get(`/products/${prod.id}`)
      .expect(200);

    expect(getRes.body.price).toBe(19.99);
  });

  test('testUpdateProduct', async () => {
    const createJson = { name: 'Gadget', price: 10.5 };

    await request(app)
      .post('/products')
      .send(createJson)
      .expect(200);

    const products = await productRepository.findAll();
    const product = products[0];

    const updateJson = { name: 'Gadget Updated', price: 15.75 };

    const putRes = await request(app)
      .put(`/products/${product.id}`)
      .send(updateJson)
      .expect(200);

    expect(putRes.body.name).toBe('Gadget Updated');
    expect(putRes.body.price).toBe(15.75);
  });

  test('testDeleteProduct', async () => {
    const createJson = { name: 'ToDelete', price: 5.0 };

    await request(app)
      .post('/products')
      .send(createJson)
      .expect(200);

    const products = await productRepository.findAll();
    const product = products[0];

    await request(app)
      .delete(`/products/${product.id}`)
      .expect(204);

    await request(app)
      .get(`/products/${product.id}`)
      .expect(404);
  });
});
