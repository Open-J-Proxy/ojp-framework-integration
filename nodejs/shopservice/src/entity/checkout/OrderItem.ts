import { Entity, PrimaryGeneratedColumn, Column, ManyToOne, JoinColumn } from 'typeorm';
import { Order } from './Order';
import { bigintTransformer } from '../transformers';

@Entity('order_items')
export class OrderItem {
  @PrimaryGeneratedColumn({ type: 'bigint' })
  id: number;

  @ManyToOne(() => Order, (order) => order.orderItems, {
    onDelete: 'CASCADE',
  })
  @JoinColumn({ name: 'order_id' })
  order: Order;

  @Column({ name: 'product_id', type: 'bigint', transformer: bigintTransformer })
  productId: number;

  @Column({ type: 'int' })
  quantity: number;

  toJSON() {
    const { order, ...rest } = this;
    return rest;
  }
}
