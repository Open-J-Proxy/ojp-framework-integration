import { Repository } from 'typeorm';
import { OrderItem } from '../../entity/checkout/OrderItem';

export class OrderItemRepository {
  constructor(private repo: Repository<OrderItem>) {}

  async save(orderItem: Partial<OrderItem>): Promise<OrderItem> {
    const entity = this.repo.create(orderItem);
    return this.repo.save(entity);
  }

  async saveAll(orderItems: Partial<OrderItem>[]): Promise<OrderItem[]> {
    const entities = this.repo.create(orderItems);
    return this.repo.save(entities);
  }

  async findById(id: number): Promise<OrderItem | null> {
    return this.repo.findOne({
      where: { id },
      relations: ['order'],
    });
  }

  async findAll(): Promise<OrderItem[]> {
    return this.repo.find({
      relations: ['order'],
      order: { id: 'ASC' },
    });
  }

  async findByOrderId(orderId: number): Promise<OrderItem[]> {
    return this.repo.find({
      where: { order: { id: orderId } },
      relations: ['order'],
      order: { id: 'ASC' },
    });
  }

  async count(): Promise<number> {
    return this.repo.count();
  }

  async delete(orderItem: OrderItem): Promise<void> {
    await this.repo.remove(orderItem);
  }

  async deleteAll(): Promise<void> {
    await this.repo.createQueryBuilder().delete().from(OrderItem).execute();
  }
}
