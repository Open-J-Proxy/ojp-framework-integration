import { getTestContext, closeTestContext } from './setup';
import { UserRepository } from '../src/repository/catalog/UserRepository';
import { ProductRepository } from '../src/repository/catalog/ProductRepository';
import { ReviewRepository } from '../src/repository/catalog/ReviewRepository';
import { User } from '../src/entity/catalog/User';
import { Product } from '../src/entity/catalog/Product';
import { Review } from '../src/entity/catalog/Review';

describe('CatalogRepositoryIT', () => {
  let userRepository: UserRepository;
  let productRepository: ProductRepository;
  let reviewRepository: ReviewRepository;

  beforeAll(async () => {
    const ctx = await getTestContext();
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
  });

  // ── User ────────────────────────────────────────────────────────────────

  test('testUserSaveAll', async () => {
    const u1 = user('alice', 'alice@example.com');
    const u2 = user('bob', 'bob@example.com');

    const saved = await userRepository.saveAll([u1, u2]);

    expect(saved).toHaveLength(2);
    saved.forEach((u) => expect(u.id).toBeDefined());
  });

  test('testUserFindAll', async () => {
    await userRepository.saveAll([
      user('alice', 'alice@example.com'),
      user('bob', 'bob@example.com'),
      user('charlie', 'charlie@example.com'),
    ]);

    const all = await userRepository.findAll();

    expect(all).toHaveLength(3);
  });

  test('testUserDeleteAll', async () => {
    await userRepository.saveAll([
      user('alice', 'alice@example.com'),
      user('bob', 'bob@example.com'),
    ]);
    expect(await userRepository.count()).toBe(2);

    await userRepository.deleteAll();

    const remaining = await userRepository.findAll();
    expect(remaining).toHaveLength(0);
  });

  // ── Product ──────────────────────────────────────────────────────────────

  test('testProductSaveAll', async () => {
    const p1 = product('Widget', 9.99);
    const p2 = product('Gadget', 19.99);

    const saved = await productRepository.saveAll([p1, p2]);

    expect(saved).toHaveLength(2);
    saved.forEach((p) => expect(p.id).toBeDefined());
  });

  test('testProductFindAll', async () => {
    await productRepository.saveAll([
      product('Widget', 9.99),
      product('Gadget', 19.99),
      product('Doohickey', 4.5),
    ]);

    const all = await productRepository.findAll();

    expect(all).toHaveLength(3);
  });

  test('testProductDeleteAll', async () => {
    await productRepository.saveAll([
      product('Widget', 9.99),
      product('Gadget', 19.99),
    ]);
    expect(await productRepository.count()).toBe(2);

    await productRepository.deleteAll();

    const remaining = await productRepository.findAll();
    expect(remaining).toHaveLength(0);
  });

  // ── Review ───────────────────────────────────────────────────────────────

  test('testReviewSaveAll', async () => {
    const u = await userRepository.save(user('alice', 'alice@example.com'));
    const p = await productRepository.save(product('Widget', 9.99));

    const saved = await reviewRepository.saveAll([
      review(u, p, 5, 'Excellent!'),
      review(u, p, 3, 'Average'),
    ]);

    expect(saved).toHaveLength(2);
    saved.forEach((r) => expect(r.id).toBeDefined());
  });

  test('testReviewFindAll', async () => {
    const u = await userRepository.save(user('alice', 'alice@example.com'));
    const p = await productRepository.save(product('Widget', 9.99));
    await reviewRepository.saveAll([
      review(u, p, 5, 'Excellent!'),
      review(u, p, 4, 'Good'),
      review(u, p, 2, 'Disappointing'),
    ]);

    const all = await reviewRepository.findAll();

    expect(all).toHaveLength(3);
  });

  test('testReviewDeleteAll', async () => {
    const u = await userRepository.save(user('alice', 'alice@example.com'));
    const p = await productRepository.save(product('Widget', 9.99));
    await reviewRepository.saveAll([
      review(u, p, 5, 'Excellent!'),
      review(u, p, 3, 'Average'),
    ]);
    expect(await reviewRepository.count()).toBe(2);

    await reviewRepository.deleteAll();

    const remaining = await reviewRepository.findAll();
    expect(remaining).toHaveLength(0);
  });

  // ── Helpers ──────────────────────────────────────────────────────────────

  function user(username: string, email: string): Partial<User> {
    const u = new User();
    u.username = username;
    u.email = email;
    return u;
  }

  function product(name: string, price: number): Partial<Product> {
    const p = new Product();
    p.name = name;
    p.price = price;
    return p;
  }

  function review(
    u: User,
    p: Product,
    rating: number,
    comment: string
  ): Partial<Review> {
    const r = new Review();
    r.user = u;
    r.product = p;
    r.rating = rating;
    r.comment = comment;
    return r;
  }
});
