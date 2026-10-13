import request from 'supertest';
import { getTestContext, closeTestContext } from './setup';
import { UserRepository } from '../src/repository/catalog/UserRepository';
import { ProductRepository } from '../src/repository/catalog/ProductRepository';
import { ReviewRepository } from '../src/repository/catalog/ReviewRepository';
import { User } from '../src/entity/catalog/User';
import { Product } from '../src/entity/catalog/Product';
import { Express } from 'express';

describe('ReviewControllerIT', () => {
  let app: Express;
  let userRepository: UserRepository;
  let productRepository: ProductRepository;
  let reviewRepository: ReviewRepository;

  let user: User;
  let product: Product;

  beforeAll(async () => {
    const ctx = await getTestContext();
    app = ctx.app;
    userRepository = ctx.userRepository;
    productRepository = ctx.productRepository;
    reviewRepository = ctx.reviewRepository;
  });

  afterAll(async () => {
    await closeTestContext();
  });

  beforeEach(async () => {
    await reviewRepository.deleteAll();
    await userRepository.deleteAll();
    await productRepository.deleteAll();

    const userObj = new User();
    userObj.username = 'bob';
    userObj.email = 'bob@example.com';
    user = await userRepository.save(userObj);

    const productObj = new Product();
    productObj.name = 'SuperToy';
    productObj.price = 99.99;
    product = await productRepository.save(productObj);
  });

  test('testCreateAndGetReview', async () => {
    const reviewJson = {
      user: { id: user.id },
      product: { id: product.id },
      rating: 5,
      comment: 'Awesome!',
    };

    const postRes = await request(app)
      .post('/reviews')
      .send(reviewJson)
      .expect(200);

    expect(postRes.body.id).toBeDefined();
    expect(postRes.body.rating).toBe(5);

    const getRes = await request(app)
      .get('/reviews')
      .expect(200);

    expect(getRes.body.content).toBeDefined();
    expect(getRes.body.content.length).toBeGreaterThan(0);
  });

  test('testDeleteReview', async () => {
    const reviewJson = {
      user: { id: user.id },
      product: { id: product.id },
      rating: 3,
      comment: 'Not great',
    };

    const postRes = await request(app)
      .post('/reviews')
      .send(reviewJson)
      .expect(200);

    expect(postRes.body.id).toBeDefined();
    const reviewId = postRes.body.id;

    await request(app)
      .delete(`/reviews/${reviewId}`)
      .expect(204);

    await request(app)
      .get(`/reviews/${reviewId}`)
      .expect(404);
  });
});
