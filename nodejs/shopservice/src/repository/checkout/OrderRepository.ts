import { Repository } from 'typeorm';
import { Order } from '../../entity/checkout/Order';
import { OrderItem } from '../../entity/checkout/OrderItem';

export class OrderRepository {
  constructor(private repo: Repository<Order>) {}

  async save(order: Partial<Order>): Promise<Order> {
    const entity = this.repo.create(order);
    if (entity.orderItems && entity.orderItems.length > 0) {
      for (const item of entity.orderItems) {
        item.order = entity;
      }
    }
    return this.repo.save(entity);
  }

  async saveAll(orders: Partial<Order>[]): Promise<Order[]> {
    const entities = orders.map((o) => {
      const entity = this.repo.create(o);
      if (entity.orderItems && entity.orderItems.length > 0) {
        for (const item of entity.orderItems) {
          item.order = entity;
        }
      }
      return entity;
    });
    return this.repo.save(entities);
  }

  async findById(id: number): Promise<Order | null> {
    return this.repo.findOne({
      where: { id },
      relations: ['orderItems'],
      order: {
        orderItems: {
          id: 'ASC',
        },
      },
    });
  }

  async findAll(options?: { page?: number; size?: number }): Promise<Order[]> {
    if (options && options.size !== undefined) {
      const page = options.page || 0;
      const size = options.size;
      return this.repo.find({
        skip: page * size,
        take: size,
        relations: ['orderItems'],
        order: { id: 'ASC' },
      });
    }
    return this.repo.find({
      relations: ['orderItems'],
      order: { id: 'ASC' },
    });
  }

  async count(): Promise<number> {
    return this.repo.count();
  }

  async delete(order: Order): Promise<void> {
    await this.repo.remove(order);
  }

  async deleteAll(): Promise<void> {
    // Delete order_items first to respect foreign keys if cascading delete query builder is used
    await this.repo.manager.createQueryBuilder().delete().from(OrderItem).execute();
    await this.repo.createQueryBuilder().delete().from(Order).execute();
  }
}
