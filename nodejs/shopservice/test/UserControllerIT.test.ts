import request from 'supertest';
import { getTestContext, closeTestContext } from './setup';
import { UserRepository } from '../src/repository/catalog/UserRepository';
import { ReviewRepository } from '../src/repository/catalog/ReviewRepository';
import { Express } from 'express';

describe('UserControllerIT', () => {
  let app: Express;
  let userRepository: UserRepository;
  let reviewRepository: ReviewRepository;

  beforeAll(async () => {
    const ctx = await getTestContext();
    app = ctx.app;
    userRepository = ctx.userRepository;
    reviewRepository = ctx.reviewRepository;
  });

  afterAll(async () => {
    await closeTestContext();
  });

  beforeEach(async () => {
    await reviewRepository.deleteAll();
    await userRepository.deleteAll();
  });

  test('testCreateAndGetUser', async () => {
    const json = {
      username: 'alice',
      email: 'alice@example.com',
      createdAt: '2024-01-15T10:30:00',
    };

    const postRes = await request(app)
      .post('/users')
      .send(json)
      .expect(200);

    expect(postRes.body.id).toBeDefined();
    expect(postRes.body.username).toBe('alice');
    expect(postRes.body.createdAt).toBeDefined();

    const users = await userRepository.findAll();
    const user = users[0];
    expect(user).toBeDefined();

    const getRes = await request(app)
      .get(`/users/${user.id}`)
      .expect(200);

    expect(getRes.body.email).toBe('alice@example.com');
    expect(getRes.body.createdAt).toBeDefined();

    const retrievedUser = await userRepository.findById(user.id);
    expect(retrievedUser).toBeDefined();
  });

  test('testUpdateUser', async () => {
    const createJson = {
      username: 'bob',
      email: 'bob@example.com',
    };

    await request(app)
      .post('/users')
      .send(createJson)
      .expect(200);

    const users = await userRepository.findAll();
    const user = users[0];

    const updateJson = {
      username: 'bob_updated',
      email: 'bob_updated@example.com',
    };

    const putRes = await request(app)
      .put(`/users/${user.id}`)
      .send(updateJson)
      .expect(200);

    expect(putRes.body.username).toBe('bob_updated');
    expect(putRes.body.email).toBe('bob_updated@example.com');
  });

  test('testDeleteUser', async () => {
    const createJson = {
      username: 'charlie',
      email: 'charlie@example.com',
    };

    await request(app)
      .post('/users')
      .send(createJson)
      .expect(200);

    const users = await userRepository.findAll();
    const user = users[0];

    await request(app)
      .delete(`/users/${user.id}`)
      .expect(204);

    await request(app)
      .get(`/users/${user.id}`)
      .expect(404);
  });
});
