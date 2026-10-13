import { getTestContext, closeTestContext } from './setup';
import { OrderRepository } from '../src/repository/checkout/OrderRepository';
import { OrderItemRepository } from '../src/repository/checkout/OrderItemRepository';
import { Order } from '../src/entity/checkout/Order';
import { OrderItem } from '../src/entity/checkout/OrderItem';

describe('CheckoutRepositoryIT', () => {
  let orderRepository: OrderRepository;
  let orderItemRepository: OrderItemRepository;

  beforeAll(async () => {
    const ctx = await getTestContext();
    orderRepository = ctx.orderRepository;
    orderItemRepository = ctx.orderItemRepository;
  });

  afterAll(async () => {
    await closeTestContext();
  });

  beforeEach(async () => {
    await orderItemRepository.deleteAll();
    await orderRepository.deleteAll();
  });

  // ── Order ────────────────────────────────────────────────────────────────

  test('testOrderSaveAll', async () => {
    const saved = await orderRepository.saveAll([order(1), order(2)]);

    expect(saved).toHaveLength(2);
    saved.forEach((o) => expect(o.id).toBeDefined());
  });

  test('testOrderFindAll', async () => {
    await orderRepository.saveAll([order(1), order(2), order(3)]);

    const all = await orderRepository.findAll();

    expect(all).toHaveLength(3);
  });

  test('testOrderDeleteAll', async () => {
    await orderRepository.saveAll([order(1), order(2)]);
    expect(await orderRepository.count()).toBe(2);

    await orderRepository.deleteAll();

    const remaining = await orderRepository.findAll();
    expect(remaining).toHaveLength(0);
  });

  // ── OrderItem ────────────────────────────────────────────────────────────

  test('testOrderItemSaveAll', async () => {
    const savedOrder = await orderRepository.save(order(1));

    const saved = await orderItemRepository.saveAll([
      orderItem(savedOrder, 10, 2),
      orderItem(savedOrder, 20, 1),
    ]);

    expect(saved).toHaveLength(2);
    saved.forEach((i) => expect(i.id).toBeDefined());
  });

  test('testOrderItemFindAll', async () => {
    const savedOrder = await orderRepository.save(order(1));
    await orderItemRepository.saveAll([
      orderItem(savedOrder, 10, 2),
      orderItem(savedOrder, 20, 1),
      orderItem(savedOrder, 30, 5),
    ]);

    const all = await orderItemRepository.findAll();

    expect(all).toHaveLength(3);
  });

  test('testOrderItemDeleteAll', async () => {
    const savedOrder = await orderRepository.save(order(1));
    await orderItemRepository.saveAll([
      orderItem(savedOrder, 10, 2),
      orderItem(savedOrder, 20, 1),
    ]);
    expect(await orderItemRepository.count()).toBe(2);

    await orderItemRepository.deleteAll();

    const remaining = await orderItemRepository.findAll();
    expect(remaining).toHaveLength(0);
  });

  // ── Helpers ──────────────────────────────────────────────────────────────

  function order(userId: number): Partial<Order> {
    const o = new Order();
    o.userId = userId;
    return o;
  }

  function orderItem(ord: Order, productId: number, quantity: number): Partial<OrderItem> {
    const i = new OrderItem();
    i.order = ord;
    i.productId = productId;
    i.quantity = quantity;
    return i;
  }
});
